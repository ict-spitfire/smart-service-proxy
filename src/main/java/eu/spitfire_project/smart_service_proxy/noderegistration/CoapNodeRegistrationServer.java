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
package eu.spitfire_project.smart_service_proxy.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.application.CoapServerApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import eu.spitfire_project.smart_service_proxy.utils.TString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.util.IPAddressUtil;

import java.net.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Oliver Kleine
 */
public class CoapNodeRegistrationServer extends CoapServerApplication {

    private static Logger log = LoggerFactory.getLogger(CoapNodeRegistrationServer.class.getName());

    private ArrayList<CoapBackend> coapBackends = new ArrayList<CoapBackend>();

    public ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private static CoapNodeRegistrationServer instance = new CoapNodeRegistrationServer();


    private CoapNodeRegistrationServer(){
        super();
        log.debug("Constructed.");

    }

    public static CoapNodeRegistrationServer getInstance(){
        return instance;
    }

    public boolean addCoapBackend(CoapBackend coapBackend){
        boolean added = coapBackends.add(coapBackend);
        if(added){
            log.debug("Registered new backend for prefix: " + coapBackend.getPrefix());
        }
        return added;
    }

    public void fakeRegistration(InetAddress inetAddress){
        executorService.schedule(new NodeRegistration(inetAddress), 0, TimeUnit.SECONDS);
    }

    /**
     * This method is invoked by the nCoAP framework whenever a new incoming CoAP request is to be processed. It only
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

        log.debug("Received request from " +
                remoteSocketAddress.getAddress().getHostAddress() + ":" + remoteSocketAddress.getPort()
                + " for resource " + coapRequest.getTargetUri());

        CoapResponse coapResponse = null;

        if(coapRequest.getTargetUri().getPath().equals("/here_i_am")){
            if(coapRequest.getCode() == Code.POST){
                if(coapRequest.getMessageType() == MsgType.CON){
                    coapResponse =  new CoapResponse(MsgType.ACK, Code.CONTENT_205);
                }

                //Node registration
                log.debug("Schedule sending of request for .well-known/core");
                executorService.schedule(new NodeRegistration(remoteSocketAddress.getAddress()),
                                                              0, TimeUnit.SECONDS);

                //Automatic annotation required
                if(coapRequest.getPayload().readableBytes() > 0){
                    log.debug("Request payload: " + coapRequest.getPayload().toString(Charset.forName("UTF-8")));
                }


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

        private Inet6Address remoteAddress;

        private Object monitor = new Object();

        private CoapResponse coapResponse;
        
        public NodeRegistration(InetAddress remoteAddress){
            super();
            this.remoteAddress = (Inet6Address) remoteAddress;
        }

        @Override
        public void run(){

            CoapBackend coapBackend = null;

            //log.debug("Look up backend for address " + remoteAddress.getHostAddress());
            for(CoapBackend backend : coapBackends){
                //Prefix is an IP address

                log.debug("remoteAddress.getHostAddress(): " + remoteAddress.getHostAddress());
                log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());
                if(remoteAddress.getHostAddress().startsWith(backend.getPrefix())){
                    coapBackend = backend;
                    log.debug("Backend found for address " + remoteAddress.getHostAddress());
                    break;
                }
                //Prefix is a DNS name
                else{
                    log.debug("Look up backend for DNS name " + remoteAddress.getHostName());
                    log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());
                    if((remoteAddress.getHostName()).equals(backend.getPrefix())){
                        coapBackend = backend;
                        log.debug("Backend found for DNS name " + remoteAddress.getHostName());
                        break;
                    }
                }
            }
            
            if(coapBackend == null){
                log.debug("[CoapNodeRegistrationServer] No backend found for IP address: " +
                        remoteAddress.getHostAddress());
                return;
            }
            
            //Only register new nodes (avoid duplicates)
            Set<Inet6Address> addressList = coapBackend.getSensorNodes();

            if(addressList.contains(remoteAddress)){
                log.debug("New here_i_am message from " + remoteAddress + ".");
                coapBackend.deleteServices(remoteAddress);
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
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (ToManyOptionsException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InvalidOptionException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (URISyntaxException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InterruptedException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            }
        }


        @Override
        public void receiveResponse(CoapResponse coapResponse) {
            log.debug("Received response for well-known/core");
            synchronized (monitor){
                this.coapResponse = coapResponse;
                monitor.notify();
            }
        }
    }
}
