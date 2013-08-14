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
package eu.spitfire.ssp.gateway.coap;

import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Guice;
import com.google.inject.Injector;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.gateway.ProxyServiceManager;
import eu.spitfire.ssp.gateway.coap.noderegistration.CoapNodeRegistrationService;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import eu.spitfire.ssp.gateway.coap.observation.CoapResourceObserver;
import eu.spitfire.ssp.gateway.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
* @author Oliver Kleine
*/

public class CoapProxyServiceManager extends ProxyServiceManager {

    public static final int COAP_SERVER_PORT = 5683;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices;
    private CoapClientApplication coapClientApplication;

    /**
     * @param prefix the unique prefix for this {@link eu.spitfire.ssp.gateway.ProxyServiceManager} instance
     */
    public CoapProxyServiceManager(String prefix){
        super(prefix);

        //create client for proxy request processing and resource observation
        coapClientApplication = new CoapClientApplication();
        this.httpRequestProcessorForCoapServices = new HttpRequestProcessorForCoapServices(coapClientApplication);

        //create server and service for node registration
        CoapServerApplication coapServerApplication = new CoapServerApplication();
        CoapNodeRegistrationService coapNodeRegistrationService = new CoapNodeRegistrationService(this,
                coapClientApplication, httpRequestProcessorForCoapServices);
        coapServerApplication.registerService(coapNodeRegistrationService);
    }

    @Override
    public void registerService(final SettableFuture<URI> uriFuture, final URI serviceUri,
                                final HttpRequestProcessor requestProcessor){

        super.registerService(uriFuture, serviceUri, requestProcessor);
        uriFuture.addListener(new Runnable(){
            @Override
            public void run() {
                try{
                    if(serviceUri.toString().contains("_minimal")){
                        log.info("Start observation of {}.", serviceUri);
                        CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
                        coapRequest.setObserveOptionRequest();
                        coapClientApplication.writeCoapRequest(coapRequest,
                                new CoapResourceObserver(CoapProxyServiceManager.this, serviceUri));
                    }
                }
                catch(Exception e){
                    log.error("Error while starting observation of service {}.", serviceUri);
                }
            }
        }, executorService);

    }

    @Override
    public HttpRequestProcessor getGui() {
        return null;
    }

    @Override
    public void initialize() {
        //registerTransparentGateway(COAP_SERVER_PORT, httpRequestProcessorForCoapServices);
    }
}