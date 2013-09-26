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
package eu.spitfire.ssp.backends;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.coap.observation.InternalObservationTimedOutMessage;
import eu.spitfire.ssp.server.pipeline.messages.InternalRegisterProxyWebserviceMessage;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.server.pipeline.messages.InternalResourceProxyUriRequest;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link BackendComponentFactory} instance is a software component to enable a client that is capable of
 * talking HTTP to communicate with an arbitrary server.
 *
 * Classes inheriting from {@link BackendComponentFactory} are responsible to provide the necessary components,
 * i.e. {@link HttpRequestProcessor} instances to translate the incoming
 * {@link HttpRequest} to whatever (potentially proprietary) protocol the actual server talks and to enable the
 * SSP framework to produce a suitable {@link HttpResponse} which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class BackendComponentFactory<T> extends SimpleChannelHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private DataOriginRegistry<T> dataOriginRegistry;
    private DataOriginAccessory<T> dataOriginReader;
    private SemanticHttpRequestProcessor<T> httpRequestProcessor;

    private Map<URI, T> resourceToDataOriginMap;
    private Multimap<T, URI> dataOriginToResourcesMultimap;
    private Map<T, DataOriginObserver> observations;

    /**
     * The {@link ScheduledExecutorService} to handle resource management specific tasks
     */
    private ScheduledExecutorService scheduledExecutorService;

    private String prefix;
    private LocalServerChannel localServerChannel;

    protected BackendComponentFactory(String prefix, LocalPipelineFactory localPipelineFactory,
                                      final ScheduledExecutorService scheduledExecutorService) throws Exception {
        this.prefix = prefix;

        //Initialize Maps for semantic resources and observations
        this.resourceToDataOriginMap = Collections.synchronizedMap(new HashMap<URI, T>());
        this.dataOriginToResourcesMultimap = Multimaps.synchronizedMultimap(HashMultimap.<T, URI>create());
        this.observations = Collections.synchronizedMap(new HashMap<T, DataOriginObserver>());

        this.scheduledExecutorService = scheduledExecutorService;

        //create local channel for internal messages related to this backend
        DefaultLocalServerChannelFactory internalChannelFactory = new DefaultLocalServerChannelFactory();
        this.localServerChannel = internalChannelFactory.newChannel(localPipelineFactory.getPipeline());
        this.localServerChannel.getPipeline().addLast("Backend Manager", this);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        if(me.getMessage() instanceof InternalRemoveResourcesMessage){
            InternalRemoveResourcesMessage message = (InternalRemoveResourcesMessage) me.getMessage();

            URI resourceUri = message.getResourceUri();
            T dataOrigin = resourceToDataOriginMap.remove(resourceUri);
            if(dataOrigin != null){
                log.info("Removed resource {} from data origin {} from list of registered resources.",
                        resourceUri, dataOrigin);
            }
        }

        ctx.sendDownstream(me);
    }

    public final LocalServerChannel getLocalServerChannel(){
       return this.localServerChannel;
    }

    public final ScheduledExecutorService getScheduledExecutorService(){
        return this.scheduledExecutorService;
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
        //create and register service to list the backends resources
        createListOfRegisteredResourcesGui();

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
        resourceToDataOriginMap.put(resourceUri, dataOrigin);
        dataOriginToResourcesMultimap.put(dataOrigin, resourceUri);
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
        return resourceToDataOriginMap.get(resourceUri);
    }

    public final Collection<URI> getResources(T dataOrigin){
        return dataOriginToResourcesMultimap.get(dataOrigin);
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

    private void createListOfRegisteredResourcesGui(){
        try{
            final WebserviceForResourcesList<T> webservice =
                    new WebserviceForResourcesList<T>(this.resourceToDataOriginMap) {
                @Override
                public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
                    super.processHttpRequest(responseFuture, httpRequest);
                }
            };

            final SettableFuture<URI> proxyResourceUriFuture = retrieveProxyUri(new URI("/resources"));

            proxyResourceUriFuture.addListener(new Runnable(){
                @Override
                public void run() {
                    try{
                        InternalRegisterProxyWebserviceMessage message
                                = new InternalRegisterProxyWebserviceMessage(proxyResourceUriFuture.get(), webservice);

                        ChannelFuture future = Channels.write(localServerChannel, message);
                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                log.info("Registered service to list resources for backend: {}", prefix);
                            }
                        });
                    }
                    catch (Exception e) {
                        log.error("This should never happen.", e);
                    }
                }
            }, this.scheduledExecutorService);


        }
        catch(Exception e){
            log.error("Could not register service to list a backends resources.", e);
        }

    };

    /**
     * Retrieves an absolute resource proxy {@link URI} for the given service {@link URI}. The proxy resource URI is
     * the URI that will be listed in the list of available proxy services.
     *
     * The originURI may be either absolute or relative, i.e. only contain path and possibly additionally query and/or
     * fragment.
     *
     * If originURI is absolute the resource proxy URI will be like
     * <code>http:<ssp-host>:<ssp-port>/?uri=resourceUri</code>. i.e. with the resourceUri in the query part of the
     * resource proxy URI. If the resourceUri is relative, i.e. without scheme, host and port, the resource proxy URI will
     * contain the path of the resourceUri in its path extended by a gateway prefix.
     *
     * @param resourceUri the {@link URI} of the origin (remote) service to get the resource proxy URI for.
     */
    public SettableFuture<URI> retrieveProxyUri(URI resourceUri){
        //Create future
        SettableFuture<URI> uriRequestFuture = SettableFuture.create();

        //Send resource proxy URI request
        InternalResourceProxyUriRequest proxyUriRequest =
                new InternalResourceProxyUriRequest(uriRequestFuture, this.prefix, resourceUri);
        Channels.write(this.localServerChannel, proxyUriRequest);

        //return future
        return uriRequestFuture;
    }

    public abstract void shutdown();

}

