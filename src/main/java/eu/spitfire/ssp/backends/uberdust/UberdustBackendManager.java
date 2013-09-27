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
package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.BackendComponentFactory;
import eu.spitfire.ssp.backends.DataOriginAccessory;
import eu.spitfire.ssp.backends.DataOriginRegistry;
import eu.spitfire.ssp.backends.LocalPipelineFactory;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.apache.log4j.Logger;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link UberdustBackendManager} instance hosts a connection to an Uberdust Server. This backend provices information about part of the devices
 * available in the uberdust server with GET and POST method functionality. If it's instanciated (by setting
 * <code>enableProxyServiceManager="uberdust"</code> in the <code>ssp.properties</code> file) it registers its WebServices
 * at the {@link eu.spitfire.ssp.server.pipeline.handler.HttpRequestDispatcher} which causes these WebServices to occur on the
 * HTML page (at <code>core://<ssp-ip>:<ssp-port>/) listing the available webServices.
 * <p/>
 * classes inheriting from {@link eu.spitfire.ssp.backends.BackendComponentFactory}.
 *
 * @author Dimitrios Amaxilatis
 */

public class UberdustBackendManager extends BackendComponentFactory<URI> {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustBackendManager.class.getName());
    /**
     * WebSocket Connection to the Uberdust Server.
     */
    private final UberdustObserver uberdustObserver;
    /**
     * Handler for the incoming requests.
     */
    private final UberdustHttpRequestProcessor httpRequestProcessor;

    /**
     * Constructor Class.
     *
     * @param prefix
     * @param localServerChannel
     * @param scheduledExecutorService
     * @throws Exception
     */
    public UberdustBackendManager(String prefix, LocalPipelineFactory localServerChannel,
                                  ScheduledExecutorService scheduledExecutorService) throws Exception {
        super(prefix, localServerChannel, scheduledExecutorService);

        //create a new Uberdsust observer.
        this.uberdustObserver = new UberdustObserver(this, scheduledExecutorService, localServerChannel);
        //create a handler for http requests and associcate with the observer.
        this.httpRequestProcessor = new UberdustHttpRequestProcessor(this, this.uberdustObserver);
    }

    @Override
    public void initialize() {
        //Nothing to do here...
    }

    @Override
    public DataOriginRegistry createDataOriginRegistry() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public DataOriginAccessory createDataOriginReader() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SemanticHttpRequestProcessor createHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    @Override
    public void shutdown() {
        //Nothing to do here...
    }


    SettableFuture<URI> registerResource(final URI resourceUri) {
        final SettableFuture<URI> resourceRegistrationFuture = registerSemanticResource(resourceUri, httpRequestProcessor);

        resourceRegistrationFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    URI resourceProxyUri = resourceRegistrationFuture.get();
                    log.info("Successfully registered resource " + resourceUri + " with proxy Uri " + resourceProxyUri);
                } catch (Exception e) {
                    log.error("Exception during registration of services from Uberdust. " + e.getMessage());
                }
            }
        }, scheduledExecutorService);

        return resourceRegistrationFuture;
    }


}
