/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire_project.smart_service_proxy.backends.coap;

import de.uniluebeck.itm.spitfire.nCoap.communication.callback.ResponseCallback;
import de.uniluebeck.itm.spitfire.nCoap.communication.core.CoapClientDatagramChannelFactory;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.utils.HttpResponseFactory;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.*;
import sun.net.util.IPAddressUtil;

import java.net.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 
 * @author Oliver Kleine
 *
 */

public class CoapBackend extends Backend{

    private static Logger log = Logger.getLogger(CoapBackend.class.getName());
        
    public static final int NODES_COAP_PORT = 5683;

    private ConcurrentHashMap<URI, URI> resources = new ConcurrentHashMap<URI, URI>();
    private HashSet<InetAddress> sensornodes = new HashSet<InetAddress>();
    private boolean enableVirtualHttp;
    
    private DatagramChannel clientChannel = CoapClientDatagramChannelFactory.getInstance().getChannel();

    /**
     * Create a new instance of the CoAPBackend-Application with a listening Datagram Socket on port 5683.
     */
    public CoapBackend(String pathPrefix, boolean enableVirtualHttp) throws Exception{
        super();
        this.enableVirtualHttp = enableVirtualHttp;
        this.pathPrefix = "/%5B" + pathPrefix;

        //Create CoAP server to handle incoming requests
        //new CoapNodeRegistrationServer(this);
    }

    @Override
    public void bind(EntityManager entityManager){
        this.entityManager = entityManager;
        entityManager.registerBackend(this, pathPrefix);
    }
    
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me){
        
        if(!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }
        
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        Object response;

        String path = httpRequest.getUri();
        int index = path.indexOf("?");
        if(index != -1){
            path = path.substring(0, index);
        }

        //Look up CoAP target URI
        URI tmpHttpMirrorURI = URI.create(entityManager.getURIBase()).resolve(path + "#").normalize();

        log.debug("tmpHttpMirrorURI: " + tmpHttpMirrorURI);

        if(log.isDebugEnabled()){
            log.debug("[CoapBackend] Look up resource for mirror URI: " + tmpHttpMirrorURI);
        }

        URI tmpTargetURI = resources.get(tmpHttpMirrorURI);

        if(tmpTargetURI == null){

            tmpHttpMirrorURI = URI.create("http://" + httpRequest.getHeader(HttpHeaders.Names.HOST) +
                    path + "#").normalize();

            if(log.isDebugEnabled()){
                log.debug("[CoapBackend] Look up resource for mirror URI: " + tmpHttpMirrorURI);
            }

            tmpTargetURI = resources.get(tmpHttpMirrorURI);
        }
        
        final URI httpMirrorURI = tmpHttpMirrorURI;
        final URI coapTargetURI = tmpTargetURI;
        
        if(coapTargetURI != null){
            try {
                //Create CoAP request
                CoapRequest coapRequest = Http2CoapConverter.convertHttpRequestToCoAPMessage(httpRequest, coapTargetURI);
                coapRequest.setResponseCallback(new ResponseCallback() {
                    @Override
                    public void receiveResponse(CoapResponse coapResponse) {
                        
                        log.debug("[CoapBackend] Received response from " + me.getRemoteAddress());
                        
                        Object response;

                        try{
                            if(coapResponse.getPayload().readableBytes() > 0){
                                response = new SelfDescription(coapResponse, httpMirrorURI);
                            }
                            else{
                                log.debug("[CoapBackend] Convert CoapResponse to HttpResponse");
                                response = Http2CoapConverter.convertCoapToHttpResponse(coapResponse,
                                        httpRequest.getProtocolVersion());
                                ((DefaultHttpResponse) response)
                                        .setContent(ChannelBuffers.
                                                wrappedBuffer("OK".getBytes(Charset.forName("UTF-8"))));
                                log.debug("[CoapBackend] Conversion CoapResponse to HttpResponse finished.");
                            }
                        }
                        catch (InvalidOptionException e) {
                            response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                            ((DefaultHttpResponse) response).setContent(coapResponse.getPayload());
                        }
                        catch(Exception e){
                            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.OK);
                            ((HttpResponse) response)
                                    .setContent(ChannelBuffers.wrappedBuffer(coapResponse.getPayload()));
                        }

//                        //------TEST!!!
//                        ChannelBuffer copy = ChannelBuffers.copiedBuffer(coapResponse.getPayload());
//                        byte[] copyArray = new byte[copy.readableBytes()];
//                        copy.readBytes(copyArray);
//                        log.debug("[CoapBackend] Payload of received packet: " +
//                                new String(copyArray, Charset.forName("UTF-8")));

                        //-------TEST ENDE!!!

                        //TODO Core-Link-Format to HTTP links.



                        //Send response
                        ChannelFuture future = Channels.write(ctx.getChannel(), response);
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                });

                //Send CoAP request
                InetSocketAddress remoteSocketAddress = new InetSocketAddress(coapTargetURI.getHost(), coapTargetURI.getPort());
                ChannelFuture future = Channels.write(clientChannel, coapRequest, remoteSocketAddress);

                if(log.isDebugEnabled()){
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            log.debug("[CoapBackend] CoAP request sent to " + coapTargetURI);
                        }
                    });
                }

                return;
            }
            catch (MethodNotAllowedException e) {
                //If there is no equivalent CoAP code for the HTTP request method
                response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED);

                if(log.isDebugEnabled()){
                    log.debug("[CoapBackend] Method (" + e.getMethod() + ") not allowed: " + httpMirrorURI);
                }
            }
        }
        else{
            response = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);

            if(log.isDebugEnabled()){
                log.debug("[CoapBackend] Resource not found: " + httpMirrorURI);
            }
        }
        
        ChannelFuture future = Channels.write(ctx.getChannel(), response);
        future.addListener(ChannelFutureListener.CLOSE);


    }
    
    /**
     * Returns the {@link InetAddress}es of the already known sensornodes
     * @return the {@link InetAddress}es of the already known sensornodes
     */
    public Set<InetAddress> getSensorNodes(){
        return sensornodes;
    }

    public void processWellKnownCoreResource(CoapResponse coapResponse, InetAddress remoteAddress){

        ChannelBuffer payloadBuffer = coapResponse.getPayload();

        log.debug("[CoAPBackend] Process ./well-known/core resource " +
                    "(size: " + payloadBuffer.readableBytes() +").");

        //register each link as new entity
        String payload = new String(payloadBuffer.array());
        String[] links = payload.split(",");

        for (String link : links){

            //Ensure a "/" at the beginning of the path
            String path = link.substring(1, link.indexOf(">"));
            if (path.indexOf("/") > 0){
                path = "/" + path;
            }
            try {

                //HTTP mirror URI (host is SSP)
                URI httpMirrorURI = createHttpMirrorURI(remoteAddress, path);

                //create CoAP URI
                URI coapTargetURI = createCoapTargetURI(remoteAddress.getHostAddress(), path);

                resources.put(httpMirrorURI, coapTargetURI);

                entityManager.entityCreated(httpMirrorURI, this);
                
                //Virtual HTTP Server for Sensor nodes
                if(enableVirtualHttp){
//                    URI virtualHttpServerUri = new URI("http://[" + remoteIP + "]" + path + "#");
//                    log.debug("[CoapBackend] New virtual HTTP service address: " + virtualHttpServerUri);
//                    resources.put(virtualHttpServerUri, coapURI);
//                    entityManager.entityCreated(virtualHttpServerUri, this);
                }
                
            }
            catch (URISyntaxException e) {
                log.fatal("[CoapBackend] Error while creating URI. This should never happen.", e);
            }
        }
    }

    @Override
    public Set<URI> getResources(){
        return resources.keySet();
    }

    /**
     * Returns the IPv6 prefix of the net the CoapBackend is responsible for (e.g. 2001:638:b157:1)
     * @return the IPv6 prefix of the net the CoapBackend is responsible for (e.g. 2001:638:b157:1)
     */
    public String getPrefix(){
        return pathPrefix.substring(4);
    }

    public URI createHttpMirrorURI(InetAddress remoteAddress, String path) throws URISyntaxException {

        String remoteIP = remoteAddress.getHostAddress();
        if(remoteIP.indexOf("%") != -1){
            remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
        }

        String encodedIP = remoteIP;

        if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
            encodedIP = "%5B" + encodedIP + "%5D";
        }

        return new URI("http://" + encodedIP + ":" + NODES_COAP_PORT + path + "#");
    }

    public URI createCoapTargetURI(String remoteIP, String path) throws URISyntaxException {
        if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
            remoteIP = "[" + remoteIP + "]";
        }
        return new URI("coap://" + remoteIP + ":" + NODES_COAP_PORT + path);
    }

    public URI createVirtualHttpServerURI(){
        return null;
    }
}