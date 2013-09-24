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
package eu.spitfire.ssp.backends.utils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link BackendManager} instance is a software component to enable a client that is capable of
 * talking HTTP to communicate with an arbitrary server.
 *
 * Classes inheriting from {@link BackendManager} are responsible to provide the necessary components,
 * i.e. {@link HttpRequestProcessor} instances to translate the incoming
 * {@link HttpRequest} to whatever (potentially proprietary) protocol the actual server talks and to enable the
 * SSP framework to produce a suitable {@link HttpResponse} which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class BackendManager<T> extends SimpleChannelHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private DataOriginRegistry<T> dataOriginRegistry;
    private DataOriginAccessory<T> dataOriginReader;
    private SemanticHttpRequestProcessor<T> httpRequestProcessor;

    private Map<URI, T> registeredResources;
    private Map<T, DataOriginObserver> observations;

    private ServiceToListResourcesPerDataOrigin<T> gui;

    /**
     * The {@link ScheduledExecutorService} to handle resource management specific tasks
     */
    protected ScheduledExecutorService scheduledExecutorService;

    private String prefix;
    private LocalServerChannel localChannel;

    protected BackendManager(String prefix, LocalPipelineFactory localPipelineFactory,
                             final ScheduledExecutorService scheduledExecutorService) throws Exception {
        this.prefix = prefix;

        this.registeredResources = Collections.synchronizedMap(new HashMap<URI, T>());
        this.scheduledExecutorService = scheduledExecutorService;

        //create local channel for internal messages related to this backend
        DefaultLocalServerChannelFactory internalChannelFactory = new DefaultLocalServerChannelFactory();
        this.localChannel = internalChannelFactory.newChannel(localPipelineFactory.getPipeline());
        this.localChannel.getPipeline().addLast("Backend Manager", this);

        //initialize observations
        this.observations = Collections.synchronizedMap(new HashMap<T, DataOriginObserver>());

        this.gui = createListOfRegisteredResourcesGui();
    }

    public final LocalServerChannel getLocalServerChannel(){
       return this.localChannel;
    }

    public final ScheduledExecutorService getScheduledExecutorService(){
        return this.scheduledExecutorService;
    }

    public final ListeningExecutorService getListeningExecutorService(){
        return MoreExecutors.listeningDecorator(this.scheduledExecutorService);
    }

    public final DataOriginRegistry<T> getDataOriginRegistry() {
        return this.dataOriginRegistry;
    }

    public final DataOriginAccessory<T> getDataOriginAccessory(){
        return this.dataOriginReader;
    }

    public SemanticHttpRequestProcessor<T> getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    /**
     * Initialize the components to run this backend, e.g. the {@link DataOriginRegistry}. This method is
     * automatically invoked by the SSP framework and itself invokes the methods {@link  #createDataOriginRegistry}
     * and {@link #initialize()} (in that order).
     */
    public final void initializeBackendComponents(){
        //Create data origin registry
        this.dataOriginRegistry = createDataOriginRegistry();

        //Create data origin reader
        this.dataOriginReader = createDataOriginReader();

        //Create Semantic HTTP request processor
        this.httpRequestProcessor = createHttpRequestProcessor();

        this.initialize();
    }

    /**
     * Method to be invoked to add a resource from a data origin
     * @param resourceUri the resource to be added
     * @param dataOrigin the data origin of the resource to be added
     */
    public final void addResource(URI resourceUri, T dataOrigin){
        registeredResources.put(resourceUri, dataOrigin);
        DataOriginObserver observer = getDataOriginObserver(dataOrigin);
        if(observer != null)
            setDataOriginObserver(dataOrigin, observer);
    }

    /**
     * Returns the {@link DataOriginObserver} instance observing the given data origin.
     * @param dataOrigin the observed data origin
     * @return the {@link DataOriginObserver} instance observing the given data origin.
     */
    public final DataOriginObserver getDataOriginObserver(T dataOrigin){
        return observations.get(dataOrigin);
    }

    /**
     * Method to relate a data origin with a proper observer instance
     * @param dataOrigin the data origin to be observed
     * @param observer the observer instance to observe the data origin
     */
    public final void setDataOriginObserver(T dataOrigin, DataOriginObserver observer){
        observations.put(dataOrigin, observer);
    }


    /**
     * Returns the data origin where the status of the given resource name is originally hosted
     * @param resourceUri the name of the resource
     * @return the data origin of the given resource
     */
    public final T getDataOrigin(URI resourceUri){
        return registeredResources.get(resourceUri);
    }

    /**
     * Method automatically invoked upon construction of the gateway instance. It is considered to contain everything
     * that is necessary to make the gateway instance working properly.
     */
    public abstract void initialize();

    /**
     * Returns the specific prefix of this gateway. If wildcard DNS is enabled, then the prefix is used as the very
     * first element of the host part of all gateway specific service URIs. If wildcard DNS is disabled, then the
     * prefix is used as the very first path element of all gatew specific service URIs.
     *
     * @return the specific prefix of this gateway
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Inheriting classes must return an instance of {@link DataOriginRegistry} capable to perform the
     * registration of new data origins identified by instances of the generic type T.
     *
     * @return an instance of {@link DataOriginRegistry} capable to perform registration of new data origins identified
     * by instances of the generic type T.
     */
    public abstract DataOriginRegistry<T> createDataOriginRegistry();

    /**
     * Inheriting classes must return an instance of {@link DataOriginAccessory} which is capable to retrieve the status
     * of all resources backed by a data origin identified via instance of the generic type T.
     *
     * @return an instance of {@link DataOriginAccessory} which is capable to retrieve the status of all resources backed
     * by a data origin identified via instance of the generic type T.
     */
    public abstract DataOriginAccessory<T> createDataOriginReader();

    public abstract SemanticHttpRequestProcessor<T> createHttpRequestProcessor();

    public abstract ServiceToListResourcesPerDataOrigin<T> createListOfRegisteredResourcesGui();

    public abstract void shutdown();

}

