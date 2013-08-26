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
package eu.spitfire.ssp.proxyservicemanagement;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.pipeline.messages.*;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link AbstractServiceManager} instance is a software component to enable a client that is capable of
 * talking HTTP to communicate with an arbitrary server.
 *
 * Classes inheriting from {@link AbstractServiceManager} are responsible to provide the necessary components,
 * i.e. {@link HttpRequestProcessor} instances to translate the incoming
 * {@link HttpRequest} to whatever (potentially proprietary) protocol the actual server talks and to enable the
 * SSP framework to produce a suitable {@link HttpResponse} which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class AbstractServiceManager {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * The {@link LocalServerChannel} instance to send internal messages, e.g. for service registration or status
     * update
     */
    protected LocalServerChannel localChannel;

    /**
     * The {@link ScheduledExecutorService} to handle resource management specific tasks
     */
    protected ScheduledExecutorService scheduledExecutorService;

    private String prefix;

    protected AbstractServiceManager(String prefix, LocalServerChannel localChannel,
                                     final ScheduledExecutorService scheduledExecutorService){
        this.prefix = prefix;
        this.localChannel = localChannel;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public abstract HttpRequestProcessor getGui();

    /**
     * Method to be called by extending classes, i.e. instances of {@link AbstractServiceManager} whenever there is a new
     * webservice to be created on the smart service proxy, if the network behind this gateway is an IP enabled
     * network.
     *
     * @param resourceUri the (original/remote) {@link URI} of the new resource to be registered.
     * @param requestProcessor the {@link HttpRequestProcessor} instance to handle incoming requests
     *
     * @return the {@link SettableFuture<URI>} containing the absolute {@link URI} for the newly registered
     * service or a {@link Throwable} if an error occurred during the registration process.
     */
    public SettableFuture<URI> registerResource(URI resourceUri, final HttpRequestProcessor requestProcessor){

        final SettableFuture<URI> resourceRegistrationFuture = SettableFuture.create();

        //Retrieve the URI the new service is available at on the proxy
        final SettableFuture<URI> proxyResourceUriFuture =  retrieveProxyUri(resourceUri);

        proxyResourceUriFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    //Register new service with the retrieved resource proxy URI
                    final URI resourceProxyUri = proxyResourceUriFuture.get();
                    ChannelFuture registrationFuture =
                            Channels.write(localChannel,
                                    new InternalRegisterResourceMessage(resourceProxyUri, requestProcessor));

                    registrationFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess())
                                resourceRegistrationFuture.set(resourceProxyUri);
                            else
                                resourceRegistrationFuture.setException(channelFuture.getCause());
                        }
                    });

                } catch (InterruptedException e) {
                    log.error("Exception during service registration process.", e);
                    resourceRegistrationFuture.setException(e);

                } catch (ExecutionException e) {
                    log.warn("Exception during service registration process.", e.getMessage());
                    resourceRegistrationFuture.setException(e);
                }

            }
        }, scheduledExecutorService);

        return resourceRegistrationFuture;
    }

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
        SettableFuture<URI> uriRequestFuture = SettableFuture.create();
        InternalResourceProxyUriRequest proxyUriRequest =
                new InternalResourceProxyUriRequest(uriRequestFuture, this.getPrefix(),
                resourceUri);
        Channels.write(localChannel, proxyUriRequest);
        return uriRequestFuture;
    }

    /**
     * Method invoked by the SSP framework to register a webservice for the GUI (if a GUI exists) and to
     * invoke the {@link #initialize()} method.
     */
    public final void registerGuiAndInitialize(){
        HttpRequestProcessor gui = this.getGui();
        if(gui != null){
            try {
                final SettableFuture<URI> guiRegistrationFuture =
                        registerResource(new URI(null, null, null, -1, "/gui", null, null), gui);

                guiRegistrationFuture.addListener(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            URI guiUri = guiRegistrationFuture.get();
                            log.debug("Successfully registered GUI at {}", guiUri);
                        }
                        catch (Exception e) {
                            log.error("Exception while registering GUI", e);
                        }
                    }
                }, scheduledExecutorService);
            }
            catch (URISyntaxException e) {
                log.error("This should never happen.", e);
            }
        }
        initialize();
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

    public abstract void shutdown();
}

