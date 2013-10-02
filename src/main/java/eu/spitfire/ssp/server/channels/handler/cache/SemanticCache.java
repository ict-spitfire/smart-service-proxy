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
package eu.spitfire.ssp.server.channels.handler.cache;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalSparqlQueryMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalUpdateResourceStatusMessage;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
* Checks whether the incoming {@link HttpRequest} can be answered with cached information. This depends on the
* existence of cached information and its age. If there is suitable information available, the request will be
* answered by sending a corresponding Object of type @link{Model} to the downstream. Otherwise the request will
* be send to the upstream unchanged to be processed by the {@link eu.spitfire.ssp.server.channels.handler.HttpRequestDispatcher}.
*
* @author Oliver Kleine
*
*/
public abstract class SemanticCache extends SimpleChannelHandler {

    public static final int DELAY_AFTER_EXPIRY = 10000;

    private Logger log = LoggerFactory.getLogger(SemanticCache.class.getName());
    private Map<URI, ScheduledFuture> expiryFutures = Collections.synchronizedMap(new HashMap<URI, ScheduledFuture>());

    private ScheduledExecutorService scheduledExecutorService;

    protected SemanticCache(ScheduledExecutorService scheduledExecutorService){
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh status of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh status is found, it sends this status (as an instance of {@link Model})
     * downstream to the {@link eu.spitfire.ssp.server.channels.handler.SemanticPayloadFormatter}.
     *
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me The {@link MessageEvent} potentially containing the {@link HttpRequest}
     *
     * @throws Exception in case of an error
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if (!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) me.getMessage();

        if(httpRequest.getMethod() != HttpMethod.GET){
            ctx.sendUpstream(me);
            return;
        }

        URI resourceProxyUri = new URI(httpRequest.getUri());
        if(resourceProxyUri.getQuery() != null && resourceProxyUri.getQuery().startsWith("uri=")){
            URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));


            log.debug("Lookup resource with URI: {}", resourceUri);
            InternalResourceStatusMessage cachedResource = getCachedResource(resourceUri);

            if(cachedResource != null){
                log.debug("Cached status for {} found.", resourceUri);

                ChannelFuture future = Channels.future(ctx.getChannel());
                Channels.write(ctx, future, cachedResource, me.getRemoteAddress());
                future.addListener(ChannelFutureListener.CLOSE);
            }
            else{
                log.debug("NO cached status for {} found. Try to get a fresh one.", resourceUri);
                ctx.sendUpstream(me);
            }
        }
        else{
            ctx.sendUpstream(me);
        }

    }

    /**
     * Returns an instance of {@link Model} that represents the resource identified by the given {@link URI}.
     *
     * @param resourceUri the {@link URI} identifying the wanted resource
     * @return the {@link Model} representing the status of the wanted resource
     */
    public abstract InternalResourceStatusMessage getCachedResource(URI resourceUri) throws Exception;


    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        try{
            if(me.getMessage() instanceof InternalResourceStatusMessage){
                InternalResourceStatusMessage message = (InternalResourceStatusMessage) me.getMessage();
                log.info("Received new status of {}", message.getResourceUri());

                scheduleNewExpiry(message.getResourceUri(), message.getExpiry());

                putResourceToCache(message.getResourceUri(), message.getModel());
            }

            else if(me.getMessage() instanceof InternalUpdateResourceStatusMessage){
                InternalUpdateResourceStatusMessage message = (InternalUpdateResourceStatusMessage) me.getMessage();
                URI resourceUri = new URI(message.getStatement().getSubject().toString());
                log.info("Received update for resource {}",resourceUri);

                scheduleNewExpiry(resourceUri, message.getExpiry());
                updateStatement(message.getStatement());
            }

            else if(me.getMessage() instanceof InternalSparqlQueryMessage){
                InternalSparqlQueryMessage message = (InternalSparqlQueryMessage) me.getMessage();
                processSparqlQuery(message.getQueryResultFuture(), message.getQuery());
            }

            ctx.sendDownstream(me);
        }
        catch (Exception e){
            log.error("Caching error!", e);
            me.getFuture().setFailure(e);
        }
    }


    private void scheduleNewExpiry(final URI resourceUri, Date expiry){
        ScheduledFuture timeoutFuture = expiryFutures.remove(resourceUri);
        if(timeoutFuture != null)
            timeoutFuture.cancel(false);

        expiryFutures.put(resourceUri, scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                try {
                    deleteResource(resourceUri);
                }
                catch (Exception e) {
                    log.error("Could not delete resource {} from cache.", resourceUri, e);
                }
            }
        }, expiry.getTime() - System.currentTimeMillis() + DELAY_AFTER_EXPIRY, TimeUnit.MILLISECONDS));
    }


    /**
     * Insert a new resource into the cache or updated an already cached one. The expiry is given to enable the
     * cache to delete the resource status from the cache when its no longer valid.
     *
     * @param resourceUri the {@link URI} identifying the resource to be cached
     * @param resourceStatus the {@link Model} representing the resource status to be cached
     */
    public abstract void putResourceToCache(URI resourceUri, Model resourceStatus) throws Exception;

    /**
     * Method to delete a cached resource from the cache.
     * @param resourceUri the {@link URI} identifying the cached resource who's status is to be deleted
     */
    public abstract void deleteResource(URI resourceUri) throws Exception;

    /**
     * Method to update a property of a resource given as the subject of the given statement
     * @param statement the {@link Statement} to be cached
     */
    public abstract void updateStatement(Statement statement) throws Exception;

    /**
     * Sets the {@link SettableFuture} with the result of the given SPARQL query
     * @param queryResultFuture the {@link SettableFuture} to contain the result of the SPARQL query after
     *                          processing
     * @param sparqlQuery the SPARQL query to process
     */
    public void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery){
        queryResultFuture.setException(new Exception("No SPARQL supported!"));
    }

    public abstract boolean supportsSPARQL();

}

