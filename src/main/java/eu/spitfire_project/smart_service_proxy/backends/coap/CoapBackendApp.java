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

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.application.CoapServerApplication;
import de.uniluebeck.itm.spitfire.nCoap.communication.callback.ResponseCallback;
import de.uniluebeck.itm.spitfire.nCoap.communication.core.CoapClientDatagramChannelFactory;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.*;
import sun.net.util.IPAddressUtil;
import sun.rmi.transport.proxy.HttpReceiveSocket;

import java.net.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * 
 * @author Oliver Kleine
 *
 */

public class CoapBackendApp extends Backend{

    private static Logger log = Logger.getLogger(CoapBackendApp.class.getName());
        
    public static final int NODES_COAP_PORT = 5683;

    private ConcurrentHashMap<URI, URI> entities = new ConcurrentHashMap<URI, URI>();
    private HashSet<InetAddress> sensornodes = new HashSet<InetAddress>();
    
    private DatagramChannel clientChannel = CoapClientDatagramChannelFactory.getInstance().getChannel();

    /**
     * Create a new instance of the CoAPBackend-Application with a listening Datagram Socket on port 5683.
     */
    public CoapBackendApp(String ipv6Prefix) throws Exception{
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
        
        log.debug("[CoapBackendApp] Message received to be converted to CoAP.");
        
        //TODO Make a real translation from HTTP to CoAP!
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        final URI mirrorURI = URI.create(entityManager.getURIBase()).resolve(httpRequest.getUri() + "#").normalize();
        
        if(log.isDebugEnabled()){
            log.debug("[CoapBackendApp] Look up resource for mirror URI: " + mirrorURI);
        }
        
        final URI targetURI = entities.get(mirrorURI);
        
        if(log.isDebugEnabled()){
            log.debug("[CoapBackendApp] Target URI to send the CoAP request to: " + targetURI);
        }

        try {
            //Create CoAp request
            CoapRequest coapRequest = Http2CoapConverter.convertHttpRequestToCoAPMessage(httpRequest, targetURI);
            coapRequest.setResponseCallback(new ResponseCallback() {
                @Override
                public void receiveCoapResponse(CoapResponse coapResponse) {

                    Object response;
                    try{
                        response = new SelfDescription(coapResponse, mirrorURI);
                    } catch (InvalidOptionException e) {
                        response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                        ((DefaultHttpResponse) response).setContent(coapResponse.getPayload());
                    }
                    
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
                        log.debug("[CoapBackendApp] CoAP request sent to " + targetURI);
                    }
                });
            }

        } catch (MethodNotAllowedException e) {
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.METHOD_NOT_ALLOWED);
            ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse);
            future.addListener(ChannelFutureListener.CLOSE);
        }


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

            //register entity at entity manager
            String encodedIP = remoteIP;
            if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
                encodedIP = "%5B" + encodedIP + "%5D";
            }
            URI mirrorURI;
            try {
                mirrorURI = entityManager.entityCreated(
                        new URI("/" + encodedIP + ":" + NODES_COAP_PORT + path), this);
                
                log.debug("[CoapBackendApp] New entity created: " + mirrorURI);
            } catch (URISyntaxException e) {
                log.fatal("[CoapBackendApp] Error while creating service mirror URI for SSP." +
                        "This should never happen.", e);
                return;
            }

            //register mapping between mirror URI and CoAP-URI
            encodedIP = remoteIP;
            if(IPAddressUtil.isIPv6LiteralAddress(encodedIP)){
                encodedIP = "[" + encodedIP + "]";
            }

            try {
                entities.put(mirrorURI, new URI("coap://" + encodedIP + ":" + NODES_COAP_PORT + path));
            } catch (URISyntaxException e) {
                log.fatal("[CoapBackendApp] Error while creating URI for the CoAP service on the sensor node." +
                        "This should never happen.", e);
            }
        }
    }
}
