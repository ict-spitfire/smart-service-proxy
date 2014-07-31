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
*  - Neither the backendName of the University of Luebeck nor the names of its contributors may be used to endorse or promote
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

import eu.spitfire.ssp.CmdLineArguments;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
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
public abstract class BackendComponentFactory<I, D extends DataOrigin<I>>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Registry<I, D> registry;

    private ScheduledExecutorService internalTasksExecutor;
    private ExecutorService ioExecutor;
    private String backendName;
    private LocalServerChannel localChannel;
    private String sspHostName;
    private int sspPort;

    private DataOriginMapper<I, D> semanticProxyWebservice;


    /**
     * Creates a new instance of {@link BackendComponentFactory}.
     *
     * @param backendName the name of the backend this factory is for
     * @param cmdLineArguments the arguments given on the command line
     * @param localChannel the {@link org.jboss.netty.channel.local.LocalServerChannel} for internal messages
     * @param internalTasksExecutor the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                              e.g. translating and forwarding requests to data origins
     * @param ioExecutor the {@link java.util.concurrent.ExecutorService} for I/O tasks
     *
     * @throws Exception if something went terribly wrong
     */
    protected BackendComponentFactory(String backendName, Configuration config,
                                      LocalServerChannel localChannel, ScheduledExecutorService internalTasksExecutor,
                                      ExecutorService ioExecutor) throws Exception {

        this.localChannel = localChannel;
        this.backendName = backendName;
        this.internalTasksExecutor = internalTasksExecutor;
        this.ioExecutor = ioExecutor;
        this.sspHostName = config.getString("ssp.hostname");
        this.sspPort = config.getInt("ssp.http.port");
    }



    /**
     * Initialize the components to run this backend, e.g. the {@link Registry}. This method is
     * automatically invoked by the SSP framework.
     */
    public final void createComponents(Configuration config) throws Exception{

        //Create semantic proxy Webservice
        this.semanticProxyWebservice = new DataOriginMapper<>(this);

        this.localChannel.getPipeline().addLast("Semantic Proxy Webservice", this.semanticProxyWebservice);

        //Create data origin registry
        this.registry = createRegistry(config);

        this.initializeComponents();
    }


    private void initializeComponents() throws Exception {
        log.info("Initialize Components for backend: {}", this.backendName);
        this.registry.startRegistry();
        this.initialize();
    }


    public abstract void initialize() throws Exception;


    public String getBackendName(){
        return this.backendName;
    }


    public String getSspHostName(){
        return this.sspHostName;
    }


    public int getSspPort(){
        return this.sspPort;
    }


    /**
     * Returns the (backend specific) {@link org.jboss.netty.channel.local.LocalChannel} to send internal messages
     *
     * @return the (backend specific) {@link org.jboss.netty.channel.local.LocalChannel} to send internal messages
     */
    public LocalServerChannel getLocalChannel(){
       return this.localChannel;
    }


    /**
     * Returns the {@link ScheduledExecutorService} to schedule tasks
     *
     * @return the {@link ScheduledExecutorService} to schedule tasks
     */
    public ScheduledExecutorService getInternalTasksExecutor(){
        return this.internalTasksExecutor;
    }


    public ExecutorService getIoExecutor(){
        return this.ioExecutor;
    }


    /**
     * Returns the {@link Registry} which is necessary to register resources from a new data origin this
     * backend is responsible for.
     *
     * @return the {@link Registry} which is necessary to resources from a new data origin
     */
    public Registry<I, D> getRegistry() {
       return this.registry;
    }


    /**
     * Returns the {@link DataOriginMapper} which is responsible to process all incoming HTTP requests
     * for the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     *
     * @return the {@link DataOriginMapper} which is responsible to process all incoming HTTP requests
     * for the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public DataOriginMapper<I, D> getDataOriginMapper(){
        return this.semanticProxyWebservice;
    }


    /**
     * Returns the {@link Observer} to observe the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed
     *
     * @return the {@link Accessor} to observe the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public abstract Observer<I, D> getObserver(D dataOrigin);


    /**
     * Returns an appropriate {@link Accessor} to access the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} or <code>null</code> if no such
     * {@link Accessor} exists.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to return the associates
     *                   {@link Accessor} for
     *
     * @return an appropriate {@link Accessor} to access the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} or <code>null</code> if no such
     * {@link Accessor} exists.
     */
    public abstract Accessor<I, D> getAccessor(D dataOrigin);


    /**
     * Inheriting classes must return an instance of {@link Registry} capable to perform the
     * registration of new data origins identified by instances of the generic type T.
     *
     * @return an instance of {@link Registry} capable to perform registration of new data origins identified
     * by instances of the generic type T.
     */
    public abstract Registry<I, D> createRegistry(Configuration config) throws Exception;


    public abstract void shutdown();
}

