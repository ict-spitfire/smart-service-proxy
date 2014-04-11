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
package eu.spitfire.ssp.backends.generic;

import eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice;
import eu.spitfire.ssp.backends.generic.messages.InternalRegisterWebserviceMessage;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.registration.DataOriginManager;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link BackendComponentFactory} instance is a software component to enable a client that is capable of
 * talking HTTP to communicate with an arbitrary server.
 *
 * Classes inheriting from {@link BackendComponentFactory} are responsible to provide the necessary components,
 * i.e. {@link eu.spitfire.ssp.server.webservices.HttpWebservice} instances to translate the incoming
 * {@link HttpRequest} to whatever (potentially proprietary) protocol the actual server talks and to enable the
 * SSP framework to produce a suitable {@link HttpResponse} which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class BackendComponentFactory<T>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private DataOriginManager<T> dataOriginManager;
    private DataOriginRegistry<T> dataOriginRegistry;
    private ScheduledExecutorService executorService;

    private String name;
    private LocalServerChannel localChannel;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix the prefix of the backend in the given config (without the ".")
     * @param config the SSP config
     * @param executorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks, e.g.
     *                        translating and forwarding requests to data origins
     *
     * @throws Exception if something went terribly wrong
     */
    protected BackendComponentFactory(String prefix, Configuration config, ScheduledExecutorService executorService)
        throws Exception {

        this.name = config.getString(prefix + ".backend.name");
        this.executorService = executorService;

        this.dataOriginManager = new DataOriginManager<>(this.name);

    }


    public void setLocalChannel(LocalServerChannel localChannel){
        this.localChannel = localChannel;
    }

    /**
     * Returns the (backend specific) {@link org.jboss.netty.channel.local.LocalChannel} to send internal messages
     *
     * @return the (backend specific) {@link org.jboss.netty.channel.local.LocalChannel} to send internal messages
     */
    public final LocalServerChannel getLocalChannel(){
       return this.localChannel;
    }

    /**
     * Returns the {@link ScheduledExecutorService} to schedule tasks
     *
     * @return the {@link ScheduledExecutorService} to schedule tasks
     */
    public final ScheduledExecutorService getExecutorService(){
        return this.executorService;
    }

//    /**
//     * Returns the {@link ListeningExecutorService} to execute tasks
//     *
//     * @return the {@link ListeningExecutorService} to execute tasks
//     */
//    public final ListeningExecutorService getListeningExecutorService(){
//        return MoreExecutors.listeningDecorator(this.executorService);
//    }

    /**
     * Returns the {@link DataOriginRegistry} which is necessary to register resources from a new data origin this
     * backend is responsible for.
     *
     * @return the {@link DataOriginRegistry} which is necessary to resources from a new data origin
     */
    public final DataOriginRegistry<T> getDataOriginRegistry() {
        return this.dataOriginRegistry;
    }

    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice} which is responsible to process all incoming HTTP requests
     * for the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     *
     * @return the {@link eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice} which is responsible to process all incoming HTTP requests
     * for the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public abstract HttpSemanticProxyWebservice getSemanticProxyWebservice(DataOrigin<T> dataOrigin);



//    /**
//     * Returns the {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} to access the given
//     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
//     *
//     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be accessed
//     *
//     * @return the {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} to access the given
//     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
//     */
//    public abstract DataOriginAccessor<T> getDataOriginAccessor(DataOrigin<T> dataOrigin);


    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.observation.DataOriginObserver} to observe the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed
     *
     * @return the {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} to observe the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public abstract DataOriginObserver<T> getDataOriginObserver(DataOrigin<T> dataOrigin);


    /**
     * Returns the {@link DataOriginManager} which is contains all resources, resp. data origins, this backend is
     * responible for.
     *
     * @return the {@link DataOriginManager} which is contains all resources, resp. data origins, this backend is
     * responible for
     */
    public final DataOriginManager<T> getDataOriginManager(){
        return this.dataOriginManager;
    }


    /**
     * Initialize the components to run this backend, e.g. the {@link DataOriginRegistry}. This method is
     * automatically invoked by the SSP framework and itself invokes the methods {@link  #createDataOriginRegistry}
     * and {@link #initialize()} (in that order).
     */
    public final void initializeBackendComponents() throws Exception{

        //Create data origin registry
        this.dataOriginRegistry = createDataOriginRegistry();

        //Register Webservice to list registered resources per data origin
        try{
            InternalRegisterWebserviceMessage registerWebserviceMessage =
                    new InternalRegisterWebserviceMessage(new URI("/" + this.name + "/resources"),
                            dataOriginManager.getWebserviceForGraphsList());

            ChannelFuture future = Channels.write(localChannel, registerWebserviceMessage);

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess())
                        log.info("Registered service to list resources for backend: {}",
                                BackendComponentFactory.this.name);
                    else
                        log.error("Could not register service to list resources for backend {}",
                                BackendComponentFactory.this.name);
                }
            });
        }
        catch (URISyntaxException e) {
            log.error("This should never happen!", e);
        }

        this.initialize();
    }


    /**
     * Method automatically invoked upon construction of the gateway instance. It is considered to contain everything
     * that is necessary to make the gateway instance working properly.
     */
    public abstract void initialize() throws Exception;

//    /**
//     * Returns the specific prefix of this gateway. If wildcard DNS is enabled, then the prefix is used as the very
//     * first element of the host part of all gateway specific service URIs. If wildcard DNS is disabled, then the
//     * prefix is used as the very first path element of all gatew specific service URIs.
//     *
//     * @return the specific prefix of this gateway
//     */
//    public String getPrefix() {
//        return prefix;
//    }

    /**
     * Inheriting classes must return an instance of {@link DataOriginRegistry} capable to perform the
     * registration of new data origins identified by instances of the generic type T.
     *
     * @return an instance of {@link DataOriginRegistry} capable to perform registration of new data origins identified
     * by instances of the generic type T.
     */
    public abstract DataOriginRegistry<T> createDataOriginRegistry();


    public abstract void shutdown();
}

