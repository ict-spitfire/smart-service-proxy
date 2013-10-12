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

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Implements the {@link BackendComponentFactory} to enable connecting to Uberdust and displaying the information
 * from the existing non IPv6 sensors and actuators available from it.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustBackendComponentFactory extends BackendComponentFactory<URI> {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustBackendComponentFactory.class.getName());
    private final int observerInsetThreadCount;
    /**
     * WebSocket Connection to the Uberdust Server.
     */
    private UberdustObserver uberdustObserver;
    /**
     * HTTP Request processor used to forward correctly the actuation post requests.
     */
    private SemanticHttpRequestProcessor httpRequestProcessor;
    private final ScheduledExecutorService scheduleExecutorService;
    private final LocalPipelineFactory localServerChannel;

    /**
     * Constructor Class.
     *
     * @param prefix
     * @param localServerChannel
     * @param scheduledExecutorService
     * @throws Exception
     */
    public UberdustBackendComponentFactory(String prefix, LocalPipelineFactory localServerChannel,
                                           ScheduledExecutorService scheduledExecutorService, String sspHostName,
                                           int sspHttpPort, int observerInsetThreadCount) throws Exception {
        super(prefix, localServerChannel, scheduledExecutorService, sspHostName, sspHttpPort);
        this.observerInsetThreadCount = observerInsetThreadCount;
        this.scheduleExecutorService = scheduledExecutorService;
        this.localServerChannel = localServerChannel;
        //create a handler for http requests and associcate with the observer.
        this.httpRequestProcessor = new UberdustHttpRequestProcessor(this);
    }

    @Override
    public void initialize() {
        //create a new Uberdsust observer.
        try {
            this.uberdustObserver = new UberdustObserver(this, scheduleExecutorService, localServerChannel,observerInsetThreadCount);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Override
    public DataOriginRegistry createDataOriginRegistry() {
        return new UberdustDataOriginRegistry(this);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SemanticHttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    @Override
    public void shutdown() {
        //Nothing to do here...
    }


    void registerResource(final Model model, final URI resourceUri) {
        ((UberdustDataOriginRegistry) getDataOriginRegistry()).registerResource(model, resourceUri);
    }
}
