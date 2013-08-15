/**
* Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
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
package eu.spitfire.ssp.proxyservicemanagement.coap;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.proxyservicemanagement.AbstractProxyServiceManager;
import eu.spitfire.ssp.proxyservicemanagement.coap.noderegistration.CoapNodeRegistrationService;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.proxyservicemanagement.coap.observation.CoapResourceObserver;
import eu.spitfire.ssp.proxyservicemanagement.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
* @author Oliver Kleine
*/

public class CoapProxyServiceManager extends AbstractProxyServiceManager {

    public static final int COAP_SERVER_PORT = 5683;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapClientApplication coapClientApplication;
    private CoapServerApplication coapServerApplication;

    public CoapProxyServiceManager(String prefix, LocalServerChannel localChannel,
                                   ScheduledExecutorService scheduledExecutorService){
        super(prefix, localChannel, scheduledExecutorService);
    }

    @Override
    public void registerResource(final SettableFuture<URI> resourceProxyUriFuture, final URI resourceUri,
                                 final HttpRequestProcessor requestProcessor){

        super.registerResource(resourceProxyUriFuture, resourceUri, requestProcessor);

        //Start observation of the newly registered resources
        resourceProxyUriFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("/light".equals(resourceUri.getPath())) {
                        log.info("Try to start observing {}.", resourceUri);

                        //create observation request
                        CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, resourceUri);
                        coapRequest.setObserveOptionRequest();

                        //send observation request
                        coapClientApplication.writeCoapRequest(coapRequest,
                                new CoapResourceObserver(resourceUri, scheduledExecutorService,
                                        CoapProxyServiceManager.this.localChannel));

                    }
                } catch (Exception e) {
                    log.error("Error while starting observation of service {}.", resourceUri);
                }
            }
        }, scheduledExecutorService);
    }

    @Override
    public HttpRequestProcessor getGui() {
        return null;
    }

    @Override
    public void initialize() {
        ThreadFactory threadFactory =  new ThreadFactoryBuilder().setNameFormat("CoAP proxy management thread#%d")
                                                                 .build();
        this.scheduledExecutorService =
                Executors.newScheduledThreadPool(1, threadFactory);

        //create client for proxyservicemanagement request processing and resource observation
        this.coapClientApplication = new CoapClientApplication();

        //create instance of HttpRequestProcessor
        HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices =
                new HttpRequestProcessorForCoapServices(coapClientApplication);

        //create server and service for node registration
        this.coapServerApplication = new CoapServerApplication();
        CoapNodeRegistrationService coapNodeRegistrationService =
                new CoapNodeRegistrationService(this, coapClientApplication, httpRequestProcessorForCoapServices);
        coapServerApplication.registerService(coapNodeRegistrationService);
    }

    @Override
    public void shutdown() {
        coapClientApplication.shutdown();
        try {
            coapServerApplication.shutdown();
        } catch (InterruptedException e) {
            log.error("Exception during CoAP server shutdown!", e);
        }
    }
}