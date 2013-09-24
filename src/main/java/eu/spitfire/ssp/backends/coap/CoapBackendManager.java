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
package eu.spitfire.ssp.backends.coap;

import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import eu.spitfire.ssp.backends.utils.*;
import eu.spitfire.ssp.backends.coap.noderegistration.CoapRegistrationWebservice;
import eu.spitfire.ssp.backends.coap.noderegistration.CoapSemanticWebserviceRegistry;
import eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapWebservices;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapBackendManager} provides all functionality to manage CoAP resources, i.e. it provides a
 * {@link CoapServerApplication} with a {@link eu.spitfire.ssp.backends.coap.noderegistration.CoapRegistrationWebservice} to enable CoAP webservers to register
 * at the SSP.
 *
 * Furthermore it provides a {@link CoapClientApplication} and a {@link eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapWebservices}
 * to forward incoming HTTP requests to the original host.
 *
 * @author Oliver Kleine
 */
public class CoapBackendManager extends BackendManager<URI> {

    public static final int COAP_SERVER_PORT = 5683;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapClientApplication coapClientApplication;
    private CoapServerApplication coapServerApplication;

    /**
     * @param prefix the prefix used for not-absolute resource URIs, e.g. <code>prefix/gui</code>
     * @param localPipelineFactory the {@link LocalPipelineFactory} to get the pipeline to send internal messages,
     *                             e.g. resource status updates.
     * @param scheduledExecutorService the {@link ScheduledExecutorService} for resource management tasks.
     */
    public CoapBackendManager(String prefix, LocalPipelineFactory localPipelineFactory,
                              ScheduledExecutorService scheduledExecutorService) throws Exception{
        super(prefix, localPipelineFactory, scheduledExecutorService);

        //create instances of CoAP client and server applications
        this.coapClientApplication = new CoapClientApplication();

        InetSocketAddress coapServerSocketAddress =
                new InetSocketAddress("2001:638:70a:b157:5eac:4cff:fe65:2aee", 5683);
        this.coapServerApplication = new CoapServerApplication(coapServerSocketAddress);
    }

    /**
     * Returns the {@link CoapClientApplication} to send requests and receive responses and update notifications
     * @return the {@link CoapClientApplication} to send requests and receive responses and update notifications
     */
    public CoapClientApplication getCoapClientApplication() {
        return this.coapClientApplication;
    }

    /**
     * Returns the {@link CoapServerApplication} to host CoAP Webservices
     * @return the {@link CoapServerApplication} to host CoAP Webservices
     */
    public CoapServerApplication getCoapServerApplication(){
        return this.coapServerApplication;
    }

//    /**
//     * Returns the {@link eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapWebservices} to retrieve the status of resources backed by
//     * a CoAP Webservice
//     * @return the {@link eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapWebservices} to retrieve the status of resources backed by
//     * a CoAP Webservice
//     */
//    public HttpRequestProcessorForCoapWebservices getHttpRequestProcessor(){
//        return this.httpRequestProcessorForCoapResources;
//    }

    @Override
    public ServiceToListResourcesPerDataOrigin<URI> createListOfRegisteredResourcesGui() {
        return null;
    }

    @Override
    public void initialize() {
        this.coapServerApplication.registerService(new CoapRegistrationWebservice(this));
    }

    @Override
    public DataOriginRegistry<URI> createDataOriginRegistry() {
        return new CoapSemanticWebserviceRegistry(this);
    }

    @Override
    public DataOriginAccessory<URI> createDataOriginReader() {
        return new CoapWebserviceDataOriginAccessory(this);
    }

    @Override
    public SemanticHttpRequestProcessor<URI> createHttpRequestProcessor() {
        return new HttpRequestProcessorForCoapWebservices(this);
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