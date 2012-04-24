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
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import org.apache.log4j.Logger;
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Oliver Kleine
 */
public class CoapNodeRegistrationServer extends CoapServerApplication {

    private static Logger log = Logger.getLogger(CoapNodeRegistrationServer.class.getName());

    private ArrayList<CoapBackend> coapBackends = new ArrayList<CoapBackend>();

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

    private static CoapNodeRegistrationServer instance = new CoapNodeRegistrationServer();


    private CoapNodeRegistrationServer(){
        super();
        log.debug("[CoapNodeRegistrationServer] Constructed.");
    }

    public static CoapNodeRegistrationServer getInstance(){
        return instance;
    }

    public boolean addCoapBackend(CoapBackend coapBackend){
        boolean added = coapBackends.add(coapBackend);
        if(added){
            log.debug("[CoapNodeRegistrationServer] Registered new backend for prefix: " + coapBackend.getPathPrefix());
        }
        return added;
    }



    /**
     * This method is invoked by the Netty framework whenever a new incoming CoAP request is to be processed. It only
     * accepts requests with {@link Code#GET} for the resource /here_i_am. All other requests will cause failure
     * responses ({@link Code#NOT_FOUND_404} for other resources or {@link Code#METHOD_NOT_ALLOWED_405} for
     * other methods).
     * 
     * @param coapRequest
     * @param remoteSocketAddress
     * @return
     */
    @Override
    public CoapResponse receiveCoapRequest(CoapRequest coapRequest, InetSocketAddress remoteSocketAddress) {

        log.debug("[CoapNodeRegistrationServer] Received request from " +
                remoteSocketAddress.getAddress().getHostAddress() + ":" + remoteSocketAddress.getPort()
                + " for resource " + coapRequest.getTargetUri());

        CoapResponse coapResponse = null;

        if(coapRequest.getTargetUri().getPath().equals("/here_i_am")){
            if(coapRequest.getCode() == Code.POST){
                if(coapRequest.getMessageType() == MsgType.CON){
                    coapResponse =  new CoapResponse(MsgType.ACK, Code.CONTENT_205);
                }
                log.debug("[CoapNodeRegistrationServer] Schedule sending of request for .well-known/core");
                executorService.schedule(new NodeRegistration(remoteSocketAddress.getAddress()), 0, TimeUnit.SECONDS);
            }
            else{
                coapResponse = new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
            }
        }
        else{
            coapResponse = new CoapResponse(Code.NOT_FOUND_404);
        }

        return coapResponse;
    }

    //Handles the registration process for new nodes in a new thread
    private class NodeRegistration extends CoapClientApplication implements Runnable{

        private InetAddress remoteAddress;

        private Object monitor = new Object();

        private CoapResponse coapResponse;
        
        public NodeRegistration(InetAddress remoteAddress){
            super();
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void run(){
            
            CoapBackend coapBackend = null;
            
            for(CoapBackend backend : coapBackends){
                if(remoteAddress.getHostAddress().startsWith(backend.getIpv6Prefix())){
                    coapBackend = backend;   
                }
            }
            
            if(coapBackend == null){
                log.debug("[CoapNodeRegistrationServer] No backend found for IPv6 address: " +
                        remoteAddress.getHostAddress());
                return;
            }
            
            //Only register new nodes (avoid duplicates)
            Set<InetAddress> addressList = coapBackend.getSensorNodes();

            if(addressList.contains(remoteAddress)){
                log.debug("[CoapNodeRegistration] Remote address already known.");
                return;
            }

            //Add new sensornode to the list of known nodes
            coapBackend.getSensorNodes().add(remoteAddress);
            if(log.isDebugEnabled()){
                log.debug("[CoapNodeRegistration] New sensor node: " + remoteAddress.getHostAddress());
            }

            try {
                //Send request to the .well-known/core resource of the new sensornode
                String remoteIP = remoteAddress.getHostAddress();
                //Remove eventual scope ID
                if(remoteIP.indexOf("%") != -1){
                    remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
                }
                if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
                    remoteIP = "[" + remoteIP + "]";
                }
                URI targetURI = new URI("coap://" + remoteIP + ":5683/.well-known/core");
                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI, this);

                synchronized (monitor){
                    //Write request for .well-knwon/core
                    writeCoapRequest(coapRequest);
                    if(log.isDebugEnabled()){
                        log.debug("[CoapNodeRegistration] Request for /.well-known/core resource at: " +
                                remoteAddress.getHostAddress() + " written.");
                    }

                    //Wait for the response
                    while(coapResponse == null){
                        monitor.wait();
                    }

                    //Process the response
                    coapBackend.processWellKnownCoreResource(coapResponse, remoteAddress);
                }

            } catch (InvalidMessageException e) {
                log.fatal("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (ToManyOptionsException e) {
                log.fatal("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InvalidOptionException e) {
                log.fatal("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (URISyntaxException e) {
                log.fatal("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InterruptedException e) {
                log.fatal("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            }
        }


        @Override
        public void receiveCoapResponse(CoapResponse coapResponse) {
            log.debug("Received response for well-known/core");
            synchronized (monitor){
                this.coapResponse = coapResponse;
                monitor.notify();
            }
        }
    }
}
