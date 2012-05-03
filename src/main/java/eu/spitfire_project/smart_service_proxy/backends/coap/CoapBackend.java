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

import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionTable;
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
    
    private DatagramChannel clientChannel = CoapClientDatagramChannelFactory.getInstance().getChannel();
    private int heartbeatInterval;

    /**
     * Create a new instance of the CoAPBackend-Application with a listening Datagram Socket on port 5683.
     */
    public CoapBackend(int heartbeatInterval, String ipv6Prefix) throws Exception{
        super();
        //this.pathPrefix = "/%5B" + ipv6Prefix.substring(0, ipv6Prefix.lastIndexOf(":"));
        this.pathPrefix = "/%5B" + ipv6Prefix;
        this.heartbeatInterval = heartbeatInterval;

        //Create CoAP server to handle incoming requests
        //new CoapNodeRegistrationServer(this);
    }

    @Override
    public void bind(EntityManager entityManager){
        this.entityManager = entityManager;
        entityManager.registerBackend(this, pathPrefix);
    }
    
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent me){
        
        if(!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }
        
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

//        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
//                HttpResponseStatus.OK);
//
//        int internalSourcePort = ((InetSocketAddress) (ctx.getChannel().getRemoteAddress())).getPort();

//        httpResponse.setContent(ChannelBuffers.wrappedBuffer(("Alles gut! Zieladresse war: "
//                + ConnectionTable.getInstance().getTcpRequest(internalSourcePort)).
//                getBytes(Charset.forName("UTF-8"))));
//
//        ChannelFuture fut = Channels.write(ctx.getChannel(), httpResponse);
//        fut.addListener(ChannelFutureListener.CLOSE);
//
//        if(true) {
//            return;
//        }

        Object response;

        String path = httpRequest.getUri();
        int index = path.indexOf("?");
        if(index != -1){
            path = path.substring(0, index);
        }

        //Look up CoAP target URI
        URI tmpMirrorURI = URI.create(entityManager.getURIBase()).resolve(path + "#").normalize();

        if(log.isDebugEnabled()){
            log.debug("[CoapBackend] Look up resource for mirror URI: " + tmpMirrorURI);
        }

        URI tmpTargetURI = resources.get(tmpMirrorURI);

        if(tmpTargetURI == null){


            tmpMirrorURI = URI.create("http://" + httpRequest.getHeader(HttpHeaders.Names.HOST) +
                    path + "#").normalize();

            if(log.isDebugEnabled()){
                log.debug("[CoapBackend] Look up resource for mirror URI: " + tmpMirrorURI);
            }

            tmpTargetURI = resources.get(tmpMirrorURI);
        }
        
        final URI mirrorURI = tmpMirrorURI;
        final URI targetURI = tmpTargetURI;
        
        if(targetURI != null){
            try {
                //Create CoAP request
                CoapRequest coapRequest = Http2CoapConverter.convertHttpRequestToCoAPMessage(httpRequest, targetURI);
                coapRequest.setResponseCallback(new ResponseCallback() {
                    @Override
                    public void receiveCoapResponse(CoapResponse coapResponse) {
                        Object response;

                        //------TEST!!!
                        ChannelBuffer copy = ChannelBuffers.copiedBuffer(coapResponse.getPayload());
                        byte[] copyArray = new byte[copy.readableBytes()];
                        copy.readBytes(copyArray);
                        log.debug("[CoapBackend] Payload of received packet: " +
                                new String(copyArray, Charset.forName("UTF-8")));

                        //-------TEST ENDE!!!

                        //TODO Core-Link-Format to HTTP links.
                        try{
                            response = new SelfDescription(coapResponse, mirrorURI);
                        }
                        catch (InvalidOptionException e) {
                            response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                            ((DefaultHttpResponse) response).setContent(coapResponse.getPayload());
                        }
                        catch(Exception e){
                            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.OK);
                            ((HttpResponse) response).setContent(ChannelBuffers.wrappedBuffer(copyArray));
                        }

                        //Send response
                        ChannelFuture future = Channels.write(ctx.getChannel(), response);
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                });

                //Send CoAP request
                InetSocketAddress remoteSocketAddress = new InetSocketAddress(targetURI.getHost(), targetURI.getPort());
                ChannelFuture future = Channels.write(clientChannel, coapRequest, remoteSocketAddress);

                if(log.isDebugEnabled()){
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            log.debug("[CoapBackend] CoAP request sent to " + targetURI);
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
                    log.debug("[CoapBackend] Method (" + e.getMethod() + ") not allowed: " + mirrorURI);
                }
            }
        }
        else{
            response = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);

            if(log.isDebugEnabled()){
                log.debug("[CoapBackend] Resource not found: " + mirrorURI);
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

        log.debug("[CoAPBackendApp] Process ./well-known/core resource: "
                + new String(payloadBuffer.array()));

        String remoteIP = remoteAddress.getHostAddress();
        if(remoteIP.indexOf("%") != -1){
            remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
        }
        
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
                //create mirror URI
                String encodedIP = remoteIP;
               
                if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
                    encodedIP = "%5B" + encodedIP + "%5D";
                }
                
                //DNS based mirror URI
                URI mirrorURI = new URI(entityManager.getURIBase() + "/" + encodedIP + ":" + NODES_COAP_PORT + path + "#");

                //create CoAP URI
                encodedIP = remoteIP;
                if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
                    encodedIP = "[" + encodedIP + "]";
                }
                URI coapURI = new URI("coap://" + encodedIP + ":" + NODES_COAP_PORT + path);

                resources.put(mirrorURI, coapURI);
                //entityManager.entityCreated(mirrorURI, this);
                
                //Virtual HTTP Server for Sensor nodes
                URI mirrorURI2 = new URI("http://[" + remoteIP + "]" + path + "#");
                log.debug("[CoapBackend] New virtual HTTP service address: " + mirrorURI2);
                resources.put(mirrorURI2, coapURI);
                entityManager.entityCreated(mirrorURI2, this);
                
            } catch (URISyntaxException e) {
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
    public String getIpv6Prefix(){
        return pathPrefix.substring(4);
    }
}
