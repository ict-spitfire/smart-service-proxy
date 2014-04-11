///**
//* Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
//* All rights reserved.
//*
//* Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
//* following conditions are met:
//*
//*  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
//*    disclaimer.
//*
//*  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//*    following disclaimer in the documentation and/or other materials provided with the distribution.
//*
//*  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
//*    products derived from this software without specific prior written permission.
//*
//* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
//* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
//* GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
//* LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
//* OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//*/
//package eu.spitfire.ssp.backends.coap;
//
//import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
//import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
//import eu.spitfire.ssp.backends.coap.registry.CoapWebserviceRegistry;
//import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
//import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
//import eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice;
//import org.apache.commons.configuration.Configuration;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.util.concurrent.ScheduledExecutorService;
//
///**
// * The {@link CoapBackendComponentFactory} provides all functionality to manage CoAP resources, i.e. it provides a
// * {@link CoapServerApplication} with a {@link eu.spitfire.ssp.backends.coap.registry.CoapRegistrationWebservice} to enable CoAP webservers to register
// * at the SSP.
// *
// * Furthermore it provides a {@link CoapClientApplication} and a {@link eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice}
// * to forward incoming HTTP requests to the original host.
// *
// * @author Oliver Kleine
// */
//public class CoapBackendComponentFactory extends BackendComponentFactory<URI> {
//
//    public static final int COAP_SERVER_PORT = 5683;
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private CoapClientApplication coapClientApplication;
//    private CoapServerApplication coapServerApplication;
//    private HttpSemanticProxyWebserviceForCoap httpRequestProcessor;
//
//    /**
//     * @param prefix the prefix used for not-absolute resource URIs, e.g. <code>prefix/gui</code>
//     * @param localPipelineFactory the {@link eu.spitfire.ssp.server.channels.LocalPipelineFactory} to get the channels to send internal messages,
//     *                             e.g. resource status updates.
//     * @param scheduledExecutorService the {@link ScheduledExecutorService} for resource management tasks.
//     */
//    public CoapBackendComponentFactory(String prefix, Configuration config, ScheduledExecutorService executorService)
//            throws Exception{
//
//        super(prefix, config, executorService);
//
//        //create instances of CoAP client and server applications
//        this.coapClientApplication = new CoapClientApplication();
//
//        InetSocketAddress coapServerSocketAddress = new InetSocketAddress(registrationServerAddress, COAP_SERVER_PORT);
//        this.coapServerApplication = new CoapServerApplication(coapServerSocketAddress);
//        log.info("CoAP server started (listening at {}, port {})", coapServerSocketAddress.getAddress(),
//                coapServerSocketAddress.getPort());
//
//        this.httpRequestProcessor = new HttpSemanticProxyWebserviceForCoap(this);
//    }
//
//    @Override
//    public HttpSemanticProxyWebservice getSemanticProxyWebservice() {
//        return this.httpRequestProcessor;
//    }
//
//    /**
//     * Returns the {@link CoapClientApplication} to send requests and receive responses and update notifications
//     * @return the {@link CoapClientApplication} to send requests and receive responses and update notifications
//     */
//    public CoapClientApplication getCoapClientApplication() {
//        return this.coapClientApplication;
//    }
//
//    /**
//     * Returns the {@link CoapServerApplication} to host CoAP Webservices
//     * @return the {@link CoapServerApplication} to host CoAP Webservices
//     */
//    public CoapServerApplication getCoapServerApplication(){
//        return this.coapServerApplication;
//    }
//
//    @Override
//    public void initialize() {
//        //nothing to do
//    }
//
//    @Override
//    public DataOriginRegistry<URI> createDataOriginRegistry() {
//        return new CoapWebserviceRegistry(this);
//    }
//
//
//
//    @Override
//    public void shutdown() {
//        coapClientApplication.shutdown();
//        try {
//            coapServerApplication.shutdown();
//        } catch (InterruptedException e) {
//            log.error("Exception during CoAP server shutdown!", e);
//        }
//    }
//}