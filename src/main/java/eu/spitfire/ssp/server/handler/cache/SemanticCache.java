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
package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.messages.DataOriginRemovalMessage;
import eu.spitfire.ssp.server.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.messages.DataOriginRegistrationMessage;
import eu.spitfire.ssp.server.exceptions.GraphNameAlreadyExistsException;
import eu.spitfire.ssp.server.messages.SparqlQueryMessage;
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
 * be send to the upstream unchanged to be processed by the {@link eu.spitfire.ssp.server.handler.HttpRequestDispatcher}.
 *
 * @author Oliver Kleine
 */
public abstract class SemanticCache extends SimpleChannelHandler {

    public static final int DELAY_AFTER_EXPIRY = 10000;

    private Logger log = LoggerFactory.getLogger(SemanticCache.class.getName());
    private Map<URI, ScheduledFuture> expiryFutures = Collections.synchronizedMap(new HashMap<URI, ScheduledFuture>());

    private ScheduledExecutorService scheduledExecutorService;

    protected SemanticCache(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }


    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh status of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh status is found, it sends this status (as an instance of {@link Model})
     * downstream to the {@link eu.spitfire.ssp.server.handler.SemanticPayloadFormatter}.
     *
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me  The {@link MessageEvent} potentially containing the {@link HttpRequest}
     * @throws Exception in case of an error
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent me) throws Exception {

        if (!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) me.getMessage();

        if (httpRequest.getMethod() != HttpMethod.GET) {
            ctx.sendUpstream(me);
            return;
        }

        URI resourceProxyUri = new URI(httpRequest.getUri());
        if (resourceProxyUri.getQuery() != null && resourceProxyUri.getQuery().startsWith("uri=")) {
            URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));


            log.debug("Lookup resource with URI: {}", resourceUri);
            ExpiringNamedGraphStatusMessage expiringNamedGraphStatusMessage = getNamedGraph(resourceUri);

            if (expiringNamedGraphStatusMessage != null) {
                log.debug("Cached status for {} found.", resourceUri);

                //me.getFuture().setSuccess();

                ChannelFuture future = me.getFuture();
                DownstreamMessageEvent dme = new DownstreamMessageEvent(ctx.getChannel(), future, expiringNamedGraphStatusMessage, me.getRemoteAddress());
                ctx.sendDownstream(dme);

                future.addListener(new ChannelFutureListener(){
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if(future.isSuccess())
                            log.info("Succesfully sent message to {}", me.getRemoteAddress());
                        else
                            log.error("Something went wrong", future.getCause());
                    }
                });
                return;
            }

            log.debug("NO cached status for {} found. Try to get a fresh one.", resourceUri);
        }

        ctx.sendUpstream(me);
    }


    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) {

        try {
            if(me.getMessage() instanceof DataOriginRegistrationMessage){

                DataOrigin dataOrigin = ((DataOriginRegistrationMessage) me.getMessage()).getDataOrigin();
                URI graphName = dataOrigin.getGraphName();

                if(this.containsNamedGraph(graphName))
                      me.getFuture().setFailure(new GraphNameAlreadyExistsException(graphName));

                else
                    ctx.sendDownstream(me);
            }

            if (me.getMessage() instanceof ExpiringNamedGraphStatusMessage) {
                ExpiringNamedGraphStatusMessage message = (ExpiringNamedGraphStatusMessage) me.getMessage();

                URI graphName = message.getExpiringGraph().getGraphName();
                Model namedGraph = message.getExpiringGraph().getGraph();
                Date expiry = message.getExpiringGraph().getExpiry();
                scheduleNamedGraphExpiry(graphName, expiry);

                Long startTime = System.currentTimeMillis();
                log.debug("Start put graph \"{}\" to cache.", graphName);

                putNamedGraphToCache(graphName, namedGraph);
                log.debug("Successfully put graph \"{}\" to cache after {} millis.", graphName,
                        System.currentTimeMillis() - startTime);

            }

            else if (me.getMessage() instanceof SparqlQueryMessage) {
                SparqlQueryMessage message = (SparqlQueryMessage) me.getMessage();
                processSparqlQuery(message.getQueryResultFuture(), message.getQuery());
            }

            else if(me.getMessage() instanceof DataOriginRemovalMessage){

                DataOriginRemovalMessage message = (DataOriginRemovalMessage) me.getMessage();
                URI graphName = message.getDataOrigin().getGraphName();

                deleteNamedGraph(graphName);

                ScheduledFuture timeoutFuture = expiryFutures.remove(graphName);
                if (timeoutFuture != null){
                    timeoutFuture.cancel(false);
                    log.info("Resource status timeout for {} canceled.", graphName);
                }
            }

            ctx.sendDownstream(me);
        }

        catch (Exception e) {
          log.error("Cache error! ", e);
          me.getFuture().setFailure(e);
        }
    }


    private void scheduleNamedGraphExpiry(final URI graphName, Date expiry) {
        log.info("Received new status of {} (expiry: {})", graphName, expiry);
        Long startTime = System.currentTimeMillis();
        log.debug("Schedule timeout for resource {}.", graphName);

        //Cancel old expiry (if existing)
        ScheduledFuture timeoutFuture = expiryFutures.remove(graphName);
        if (timeoutFuture != null)
            timeoutFuture.cancel(false);

        log.debug("Canceled timeout after {} millis for resource {}", System.currentTimeMillis() - startTime,
                graphName);

        //Set new expiry (if not null)
        if (expiry != null) {
            expiryFutures.put(graphName, scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        deleteNamedGraph(graphName);
                    } catch (Exception e) {
                        log.error("Could not delete resource {} from cache.", graphName, e);
                    }
                }
            }, expiry.getTime() - System.currentTimeMillis() + DELAY_AFTER_EXPIRY, TimeUnit.MILLISECONDS));
        }
    }

    /**
     * Returns a {@link Model} that contains the graph identified by the given {@link URI} as graph name. If no such
     * graph is contained in the database, it returns <code>null</code>.
     *
     * @param graphName the {@link URI} identifying the wanted resource
     *
     * @return the {@link Model} containing the graph with the given name or <code>null</code> if no such graph is
     * contained in the database.
     */
    public abstract ExpiringNamedGraphStatusMessage getNamedGraph(URI graphName) throws Exception;


    public abstract boolean containsNamedGraph(URI graphName);


    /**
     * Insert a new resource into the cache or updated an already cached one. The expiry is given to enable the
     * cache to delete the resource status from the cache when its no longer valid.
     *
     * @param graphName the {@link URI} identifying the resource to be cached
     * @param namedGraph the {@link Model} representing the resource status to be cached
     */
    public abstract void putNamedGraphToCache(URI graphName, Model namedGraph) throws Exception;


    /**
     * Method to delete a cached resource from the cache.
     *
     * @param graphName the {@link URI} identifying the cached resource who's status is to be deleted
     */
    public abstract void deleteNamedGraph(URI graphName) throws Exception;


//    /**
//     * Method to update a property of a resource given as the subject of the given statement
//     *
//     * @param statement the {@link Statement} to be cached
//     */
//    public abstract void updateStatement(Statement statement) throws Exception;

    /**
     * Sets the {@link SettableFuture} with the result of the given SPARQL query
     *
     * @param queryResultFuture the {@link SettableFuture} to contain the result of the SPARQL query after
     *                          processing
     * @param sparqlQuery       the SPARQL query to process
     */
    public void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery) {
        queryResultFuture.setException(new Exception("No SPARQL supported!"));
    }

    public abstract boolean supportsSPARQL();

}

