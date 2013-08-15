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
package eu.spitfire.ssp.gateway;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.core.pipeline.messages.InternalProxyUriRequest;
import eu.spitfire.ssp.core.pipeline.messages.InternalRegisterResourceMessage;
import eu.spitfire.ssp.core.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.core.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import org.jboss.netty.channel.Channel;
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
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link ProxyServiceManager} instance is a software component to enable a client that is capable of talking HTTP to
 * communicate with an arbitrary server.
 *
 * The {@link ProxyServiceManager} is responsible for translating the incoming {@link HttpRequest} to whatever
 * (potentially proprietary) protocol the actual server talks and to produce a suitable {@link HttpResponse}
 * which is then sent to the client.
 *
 * Furthermore it is responsible for creating all necessary sub-components to manage the communication with the
 * actual server.
 *
 * @author Oliver Kleine
 */
public abstract class ProxyServiceManager {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel internalChannel;
    protected ExecutorService executorService;

    private String prefix;

    protected ProxyServiceManager(){
        HttpRequestProcessor gui = this.getGui();
        if(gui != null){
            try {
                registerResource(SettableFuture.<URI>create(), new URI(null, null, null, -1, "/", null, null), gui);
            } catch (URISyntaxException e) {
                log.error("This should never happen.", e);
            }
        }
    }

    public void setPrefix(String prefix){
        this.prefix = prefix;
    }

    public abstract HttpRequestProcessor getGui();

    /**
     * Method to be called by extending classes, i.e. instances of {@link ProxyServiceManager} whenever there is a new
     * webservice to be created on the smart service proxy, if the network behind this gateway is an IP enabled
     * network.
     *
     * @param uriFuture the {@link SettableFuture<URI>} containing the absolute {@link URI} for the newly registered
     *                  service or a {@link Throwable} if an error occured after method completion.
*      @param resourceUri the (original/remote) {@link URI} of the new resource to be registered.
     * @param requestProcessor the {@link HttpRequestProcessor} instance to handle incoming requests
     */
    public void registerResource(final SettableFuture<URI> uriFuture, URI resourceUri,
                                 final HttpRequestProcessor requestProcessor){

        //Retrieve the URI the new service is available at on the proxy
        final SettableFuture<URI> uriRequestFuture = SettableFuture.create();
        retrieveProxyUri(uriRequestFuture, resourceUri);

        uriRequestFuture.addListener(new Runnable(){
            @Override
            public void run(){
                try {
                    //Register new service with the retrieved proxy URI
                    final URI proxyUri = uriRequestFuture.get();
                    ChannelFuture registrationFuture =
                            Channels.write(internalChannel,
                                    new InternalRegisterResourceMessage(proxyUri, requestProcessor));

                    registrationFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if(channelFuture.isSuccess())
                                uriFuture.set(proxyUri);
                            else
                                uriFuture.setException(channelFuture.getCause());
                        }
                    });

                } catch (InterruptedException e) {
                    log.error("This should never happen.", e);
                    uriFuture.setException(e);

                } catch (ExecutionException e) {
                    log.error("This should never happen.", e);
                    uriFuture.setException(e);
                }
            }
        }, executorService);
    }

    /**
     * Method to update a somehow observed resource that changed its status. This method is to be invoked by
     * observers to update the status in the cache.
     *
     * @param resourceUri the {@link URI} identifying the resource whose status was updated
     * @param resourceStatus the {@link Model} containing the actual status of the resource
     * @param expiry the expiry of this status
     */
    public void updateCachedResourceStatus(URI resourceUri, Model resourceStatus, Date expiry){
        final ResourceStatusMessage resourceStatusMessage =
                new ResourceStatusMessage(resourceUri, resourceStatus, expiry);

        ChannelFuture future = Channels.write(internalChannel, resourceStatusMessage);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.debug("Successfully updated resource {}.", resourceStatusMessage.getResourceUri());
            }
        });
    }

    public void removeResource(URI resourceUri){
        final InternalRemoveResourceMessage internalRemoveResourceMessage =
                new InternalRemoveResourceMessage(resourceUri);

        ChannelFuture future = Channels.write(internalChannel, internalRemoveResourceMessage);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.debug("Successfully removed resource {}.", internalRemoveResourceMessage.getResourceUri());
            }
        });
    }

    /**
     * Retrieves an absolute proxy {@link URI} for the given service {@link URI}. The proxy URI is the URI that
     * will be listed in the list of available proxy services.
     *
     * The originURI may be either absolute or relative, i.e. only contain path and possibly additionally query and/or
     * fragment.
     *
     * If originURI is absolute the proxy URI will be like <code>http:<ssp-host>:<ssp-port>/?uri=originUri</code>. i.e.
     * with the originUri in the query part of the proxy URI. If the originUri is relative, i.e. without scheme, host
     * and port, the proxy URI will contain the path of the originUri in its path extended by a gateway prefix.
     *
     * @param uriRequestFuture the {@link SettableFuture<URI>} to eventually contain the absolute proxy {@link URI}
     * @param originUri the {@link URI} of the origin (remote) service to get the proxy URI for.
     */
    public void retrieveProxyUri(SettableFuture<URI> uriRequestFuture, URI originUri){
        Channels.write(internalChannel, new InternalProxyUriRequest(uriRequestFuture, this.getPrefix(),
                originUri));
    }

    /**
     * This method is invoked upon construction of the gateway instance. It is considered to contain everything that is
     * necessary to make the gateway instance working properly.
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
     * @param internalChannel the {@link Channel} to send internal messages to e.g. register or update services
     */
    public void setInternalChannel(LocalServerChannel internalChannel){
        this.internalChannel = internalChannel;
    }

    /**
     * @param executorService the thread-pool to handle gateway specific tasks, e.g. register or update services
     */
    public void setExecutorService(ExecutorService executorService){
        this.executorService = executorService;
    }

    public abstract void shutdown();
}

