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
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.*;
import sun.net.util.IPAddressUtil;

import java.net.*;
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

    /**
     * Create a new instance of the CoAPBackend-Application with a listening Datagram Socket on port 5683.
     */
    public CoapBackend(String ipv6Prefix) throws Exception{
        super();
        this.pathPrefix = "/%5B" + ipv6Prefix.substring(0, ipv6Prefix.lastIndexOf(":"));

        //Create CoAP server to handle incoming requests
        new CoapNodeRegistrationServer(this);
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
        Object response;
                
        //Look up CoAP target URI
        final URI mirrorURI = URI.create(entityManager.getURIBase()).resolve(httpRequest.getUri() + "#").normalize();
        final URI targetURI = resources.get(mirrorURI);

        if(log.isDebugEnabled()){
            log.debug("[CoapBackend] Look up resource for mirror URI: " + mirrorURI);
        }

        if(targetURI != null){
            try {
                //Create CoAP request
                CoapRequest coapRequest = Http2CoapConverter.convertHttpRequestToCoAPMessage(httpRequest, targetURI);
                coapRequest.setResponseCallback(new ResponseCallback() {
                    @Override
                    public void receiveCoapResponse(CoapResponse coapResponse) {
                        Object response;
                        try{
                            response = new SelfDescription(coapResponse, mirrorURI);
                        }
                        catch (InvalidOptionException e) {
                            response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                            ((DefaultHttpResponse) response).setContent(coapResponse.getPayload());
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
                response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED);

                if(log.isDebugEnabled()){
                    log.debug("[CoapBackend] Resource not found: " + mirrorURI);
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
                URI mirrorURI = new URI(entityManager.getURIBase() + "/" + encodedIP + ":" + NODES_COAP_PORT + path + "#");

                //create CoAP URI
                encodedIP = remoteIP;
                if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
                    encodedIP = "[" + encodedIP + "]";
                }
                URI coapURI = new URI("coap://" + encodedIP + ":" + NODES_COAP_PORT + path);

                resources.put(mirrorURI, coapURI);

                
            } catch (URISyntaxException e) {
                log.fatal("[CoapBackend] Error while creating URI. This should never happen.", e);
            }
        }
    }

    @Override
    public Set<URI> getResources(){
        return resources.keySet();
    }
}
