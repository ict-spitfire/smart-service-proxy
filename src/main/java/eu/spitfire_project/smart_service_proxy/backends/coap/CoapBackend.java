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

import com.google.common.collect.HashMultimap;
import de.uniluebeck.itm.spitfire.nCoap.communication.callback.ResponseCallback;
import de.uniluebeck.itm.spitfire.nCoap.communication.core.CoapClientDatagramChannelFactory;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.noderegistration.AutoAnnotation;
import eu.spitfire_project.smart_service_proxy.utils.HttpResponseFactory;
import eu.spitfire_project.smart_service_proxy.utils.TString;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import sun.net.util.IPAddressUtil;

import java.net.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;


/**
 * 
 * @author Oliver Kleine
 *
 */

public class CoapBackend extends Backend{

    private static Logger log = Logger.getLogger(CoapBackend.class.getName());
        
    public static final int NODES_COAP_PORT = 5683;

    private HashMultimap<Inet6Address, String> services = HashMultimap.create();

    private boolean enableVirtualHttp;
    
    private DatagramChannel clientChannel = CoapClientDatagramChannelFactory.getInstance().getChannel();

    /**
     * Create a new instance of the CoAPBackend-Application with a listening Datagram Socket on port 5683.
     */
    public CoapBackend(String prefix, boolean enableVirtualHttp) throws Exception{
        super();
        this.enableVirtualHttp = enableVirtualHttp;
        this.prefix = prefix;
    }

    @Override
    public void bind(){
        EntityManager.getInstance().registerBackend(this, prefix);
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me){
        
        if(!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }
        
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        String path = httpRequest.getUri();
        int index = path.indexOf("?");
        if(index != -1){
            path = path.substring(0, index);
        }

        //Look up CoAP target URI
        try {
            final Inet6Address targetUriHostAddress =
                    (Inet6Address) InetAddress.getByName(httpRequest.getHeader("HOST"));
            log.debug("Host: " + targetUriHostAddress);

            final String targetUriPath = httpRequest.getUri();
            log.debug("Path: " + targetUriPath);

            if(services.containsEntry(targetUriHostAddress, targetUriPath)){

                log.debug("Service found!");

                final URI coapTargetURI = URI.create("coap://["
                                              + targetUriHostAddress.getHostAddress()
                                              + "]:" + NODES_COAP_PORT
                                              + "/" + targetUriPath);

                log.debug("CoAP target URI: " + coapTargetURI);

                CoapRequest coapRequest = Http2CoapConverter.convertHttpRequestToCoAPMessage(httpRequest, coapTargetURI);
                coapRequest.setResponseCallback(new ResponseCallback() {
                    @Override
                    public void receiveResponse(CoapResponse coapResponse) {

                        log.debug("Received response from " + me.getRemoteAddress());

                        Object response;

                        try{
                            if(!(coapResponse.getCode().isError()) && coapResponse.getPayload().readableBytes() > 0){
                                response = new SelfDescription(coapResponse,
                                        createHttpURIs(targetUriHostAddress, targetUriPath)[1]);
                            }
                            else{
                                log.debug("CoapResponse is error message or without payload.");
                                response = Http2CoapConverter.convertCoapToHttpResponse(coapResponse,
                                        httpRequest.getProtocolVersion());
//                                String content = ((HttpResponse) response).getStatus().toString();
//                                ((DefaultHttpResponse) response)
//                                        .setContent(ChannelBuffers.
//                                                wrappedBuffer(content.getBytes(Charset.forName("UTF-8"))));
                                log.debug("Http response " + ((HttpResponse) response).getStatus() + " created.");
                            }
                        }
//                        catch (InvalidOptionException e) {
//                            log.error("Error.", e);
//                            response = new DefaultHttpResponse(httpRequestUri.getProtocolVersion(),
//                                                               HttpResponseStatus.INTERNAL_SERVER_ERROR);
//                            ((DefaultHttpResponse) response).setContent(coapResponse.getResponsePayload());
//                        }
                        catch(Exception e){
                            log.error("Error.", e);
                            response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            ((HttpResponse) response)
                                    .setContent(ChannelBuffers.wrappedBuffer(coapResponse.getPayload()));
                        }

                        ChannelFuture future = Channels.write(ctx.getChannel(), response);
                        future.addListener(ChannelFutureListener.CLOSE);
                    }

                    @Override
                    public void receiveInternalError(String errorMessage){
                        HttpResponse response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                                HttpResponseStatus.GATEWAY_TIMEOUT);
                        byte[] payload = errorMessage.getBytes(Charset.forName("UTF-8"));
                        response.setContent(ChannelBuffers.wrappedBuffer(payload));

                        ChannelFuture future = Channels.write(ctx.getChannel(), response);
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                });

                //Send CoapRequest
                InetSocketAddress remoteSocketAddress =
                        new InetSocketAddress(coapTargetURI.getHost(), coapTargetURI.getPort());

                ChannelFuture future = Channels.write(clientChannel, coapRequest, remoteSocketAddress);

                if(log.isDebugEnabled()){
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            log.debug("CoAP request sent to " + coapTargetURI);
                        }
                    });
                }
            }
            else{
                log.debug("Service " + targetUriPath + " unknwon for host " + targetUriHostAddress + ".");
                Object response = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.NOT_FOUND);

                ChannelFuture future = Channels.write(ctx.getChannel(), response);
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
        catch (UnknownHostException e) {
            log.error("Error.", e);
        }
        catch (MethodNotAllowedException e) {
            log.error("Error.",e);
        }
    }
    
    /**
     * Returns the {@link InetAddress}es of the already known sensornodes
     * @return the {@link InetAddress}es of the already known sensornodes
     */
    public Set<Inet6Address> getSensorNodes(){
        return services.keySet();
    }

    public void deleteServices(Inet6Address serverAddress){
        log.debug("Delete services for " + serverAddress + ".");
        services.removeAll(serverAddress);
    }

    public void processWellKnownCoreResource(CoapResponse coapResponse, Inet6Address remoteAddress){

        ChannelBuffer payloadBuffer = coapResponse.getPayload();

        log.debug("Process ./well-known/core resource " +
                    "(size: " + payloadBuffer.readableBytes() +") from " + remoteAddress.getHostAddress());

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
                services.put(remoteAddress, path);

                URI[] httpURIs = createHttpURIs(remoteAddress, path);
                EntityManager.getInstance().entityCreated(httpURIs[0], this);
                EntityManager.getInstance().virtualEntityCreated(httpURIs[1], this);
                
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

        //----------- fuzzy annotation and visualizer----------------------
        String ipv6Addr = remoteAddress.getHostAddress();
        if(ipv6Addr.indexOf("%") != -1){
            ipv6Addr = ipv6Addr.substring(0, ipv6Addr.indexOf("%"));
        }
        TString mac = new TString(ipv6Addr,':');
        String macAddr = mac.getStrAtEnd();

        log.debug("MACAddr is " + macAddr);

        if(IPAddressUtil.isIPv6LiteralAddress(ipv6Addr)){
            ipv6Addr = "[" + ipv6Addr + "]";
        }

        //URI of the minimal service (containg light value) of the new sensor
        String httpRequestUri = null;
        try {
            URI uri = CoapBackend.createHttpURIs((Inet6Address) remoteAddress, "/light/_minimal")[0];
            httpRequestUri = uri.toString();
        }
        catch (URISyntaxException e) {
            log.error("Exception", e);
        }

        //httpTargetURI = "http://" + httpTargetURI+":8080/light/_minimal";
        log.debug("HTTP URI for minimal service: " + httpRequestUri);

        //String FOI = "";

        /*while (coapRequest.getPayload().readable())
         FOI += (char)coapRequest.getPayload().readByte();
     log.debug("FOI full: "+FOI);
     TString tfoi = new TString(FOI,'/');
     String foi = tfoi.getStrAtEnd();
     FOI = foi.substring(0, foi.length()-1);*/
        //log.debug("FOI extracted: "+FOI);

        AutoAnnotation.getInstance().updateDB(ipv6Addr, macAddr, httpRequestUri);
    }

    @Override
    public Set<URI> getResources(){
        HashSet<URI> result = new HashSet<URI>(services.size());
        for(Inet6Address address : services.keySet()){
            for(String path : services.get(address)){

                result.add(URI.create("http://" + address.getHostAddress()
                                     + EntityManager.SSP_HTTP_SERVER_PORT
                                     + "/" + path));
            }
        }
        return result;
    }

//    /**
//     * Returns the IPv6 prefix of the net the CoapBackend is responsible for (e.g. 2001:638:b157:1)
//     * @return the IPv6 prefix of the net the CoapBackend is responsible for (e.g. 2001:638:b157:1)
//     */
//    public String getIPv6Prefix(){
//        return prefix.substring(4);
//    }

    public static URI[] createHttpURIs(Inet6Address remoteAddress, String path) throws URISyntaxException {

        URI[] result = new URI[2];

        String remoteIP = remoteAddress.getHostAddress();
        if(remoteIP.indexOf("%") != -1){
            remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
        }

        log.debug("Remote IP original: " + remoteIP);
        //remove leading zeros per block
        remoteIP = remoteIP.replaceAll(":0000", ":0");
        remoteIP = remoteIP.replaceAll(":000", ":0");
        remoteIP = remoteIP.replaceAll(":00", ":0");
        remoteIP = remoteIP.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");
        log.debug("Remote IP shortened 1: " + remoteIP);

        //return shortened IP
        remoteIP = remoteIP.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
        log.debug("Remote IP shortened 2: " + remoteIP);

        result[0] = new URI("http://" + remoteIP.replace(":", "-")
                            + "." + EntityManager.DNS_WILDCARD_POSTFIX
                            + ":" + EntityManager.SSP_HTTP_SERVER_PORT
                            + path);

        result[1] = new URI("http://[" + remoteIP + "]" + path);
;
        return result;
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