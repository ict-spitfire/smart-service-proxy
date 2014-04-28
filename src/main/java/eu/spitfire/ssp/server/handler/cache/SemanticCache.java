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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.messages.*;
import eu.spitfire.ssp.server.exceptions.GraphNameAlreadyExistsException;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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

    private ScheduledExecutorService internalTasksExecutorService;
    private ExecutorService ioExecutorService;

    protected SemanticCache(ExecutorService ioExecutorService, ScheduledExecutorService internalTasksExecutorService) {
        this.ioExecutorService = ioExecutorService;
        this.internalTasksExecutorService = internalTasksExecutorService;
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
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if (me.getMessage() instanceof HttpRequest){
            HttpRequest httpRequest = (HttpRequest) me.getMessage();

            if(httpRequest.getMethod() == HttpMethod.GET){
                String uriQuery = new URI(httpRequest.getUri()).getQuery();

                if(uriQuery != null){
                    String[] queryParts = uriQuery.split("&");
                    for(String queryPart : queryParts){
                        if(queryPart.startsWith("graph=")){
                            URI graphName = new URI(queryPart.substring(6));
                            graphRequestReceived(ctx, me, graphName);
                            return;
                        }

                        else if(queryPart.startsWith("resource=")){
                            URI resourceName = new URI(queryPart.substring(9));
                            resourceRequestReceived(ctx, me, resourceName);
                            return;
                        }
                    }
                }
            }

            else if(httpRequest.getMethod() == HttpMethod.POST &&
                    "/sparql".equals(new URI(httpRequest.getUri()).getPath())){

                sparqlRequestReceived(ctx, me);
                return;
            }
        }

        ctx.sendUpstream(me);
    }


    private void sparqlRequestReceived(final ChannelHandlerContext ctx, final MessageEvent me) {
        final HttpVersion httpVersion = ((HttpRequest) me.getMessage()).getProtocolVersion();

        try{
            Query sparqlQuery = extractSparqlQuery((HttpRequest) me.getMessage());

            Futures.addCallback(processSparqlQuery(sparqlQuery), new FutureCallback<GraphStatusMessage>() {

                @Override
                public void onSuccess(GraphStatusMessage result) {
                    ChannelFuture future = Channels.future(ctx.getChannel());
                    Channels.write(ctx, future, result, me.getRemoteAddress());

                    future.addListener(ChannelFutureListener.CLOSE);
                }

                @Override
                public void onFailure(Throwable t) {
                    ChannelFuture future = Channels.future(ctx.getChannel());
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                    Channels.write(ctx, future, httpResponse, me.getRemoteAddress());
                    future.addListener(ChannelFutureListener.CLOSE);
                }

            }, ioExecutorService);
        }

        catch (Exception e) {
            ChannelFuture future = Channels.future(ctx.getChannel());
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                    HttpResponseStatus.BAD_REQUEST, e.getMessage());

            Channels.write(ctx, future, httpResponse, me.getRemoteAddress());
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }



    private Query extractSparqlQuery(HttpRequest httpRequest) throws Exception{

        String contentType = httpRequest.headers().get(HttpHeaders.Names.CONTENT_TYPE);
        Charset charset = Charset.forName("UTF-8");

        if(contentType != null && contentType.contains("charset=")){
            String tmp = contentType.substring(contentType.indexOf("charset=") + 8);

            if(Charset.availableCharsets().containsKey(tmp))
                charset = Charset.forName(tmp);
        }

        log.debug("Use charset {} to read SPARQL query from HTTP request.", charset);

        String queryString = httpRequest.getContent().toString(charset);

        log.debug("SPARQL query read from HTTP request: {}", queryString);

        return QueryFactory.create(queryString);
    }



    private void resourceRequestReceived(final ChannelHandlerContext ctx, final MessageEvent me,
                                         final URI resourceName){

        log.debug("Lookup resource with name: {}", resourceName);

        this.internalTasksExecutorService.execute(new Runnable(){

            @Override
            public void run() {
                try{
                    Query sparqlQuery = QueryFactory.create(
                        "SELECT ?s ?p ?o WHERE {?s ?p ?o . FILTER (?s = <" + resourceName + ">)}"
                    );

                    Futures.addCallback(processSparqlQuery(sparqlQuery), new FutureCallback<GraphStatusMessage>(){

                        @Override
                        public void onSuccess(GraphStatusMessage result) {
                            ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                            Channels.write(ctx, channelFuture, result, me.getRemoteAddress());

                            channelFuture.addListener(ChannelFutureListener.CLOSE);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            HttpVersion httpVersion = ((HttpRequest) me.getMessage()).getProtocolVersion();
                            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                            ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                            Channels.write(ctx, channelFuture, httpResponse, me.getRemoteAddress());

                            channelFuture.addListener(ChannelFutureListener.CLOSE);
                        }

                    }, SemanticCache.this.ioExecutorService);
                }

                catch(Exception ex){
                    HttpVersion httpVersion = ((HttpRequest) me.getMessage()).getProtocolVersion();
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

                    ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                    Channels.write(ctx, channelFuture, httpResponse, me.getRemoteAddress());

                    channelFuture.addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
    }


    private void graphRequestReceived(final ChannelHandlerContext ctx, final MessageEvent me, final URI graphName)
            throws Exception{

        log.debug("Lookup graph \"{}\".", graphName);

        this.internalTasksExecutorService.execute(new Runnable(){

            @Override
            public void run() {

                Futures.addCallback(getNamedGraph(graphName), new FutureCallback<GraphStatusMessage>() {

                    @Override
                    public void onSuccess(GraphStatusMessage result) {

                        if(result instanceof ExpiringGraphStatusMessage){
                            ChannelFuture future = Channels.future(ctx.getChannel());
                            Channels.write(ctx, future, result, me.getRemoteAddress());

                            future.addListener(ChannelFutureListener.CLOSE);
                        }

                        else{
                            ctx.sendUpstream(me);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        ctx.sendUpstream(me);
                    }

                }, SemanticCache.this.ioExecutorService);
            }
        });
    }


    @Override
    public void writeRequested(ChannelHandlerContext ctx, final MessageEvent me) {

        try {
            if(me.getMessage() instanceof DataOriginRegistrationMessage){

                DataOrigin dataOrigin = ((DataOriginRegistrationMessage) me.getMessage()).getDataOrigin();
                final URI graphName = dataOrigin.getGraphName();

                Futures.addCallback(this.containsNamedGraph(graphName), new FutureCallback<Boolean>() {

                    @Override
                    public void onSuccess(Boolean alreadyContained) {
                        if(alreadyContained){
                            log.warn("Graph \"{}\" was already contained in cache!", graphName);
                            me.getFuture().setFailure(new GraphNameAlreadyExistsException(graphName));
                        }
                        else{
                            log.info("Graph \"{}\" not yet contained in cache!", graphName);
                            me.getFuture().setSuccess();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Failed to register graph \"{}\"!", graphName, t);
                        me.getFuture().setFailure(new GraphNameAlreadyExistsException(graphName));
                    }
                });
            }


            if (me.getMessage() instanceof ExpiringNamedGraphStatusMessage) {
                internalTasksExecutorService.execute(new Runnable(){

                    @Override
                    public void run() {
                        ExpiringNamedGraphStatusMessage message = (ExpiringNamedGraphStatusMessage) me.getMessage();

                        final URI graphName = message.getExpiringGraph().getGraphName();

                        Model namedGraph = message.getExpiringGraph().getGraph();
                        Date expiry = message.getExpiringGraph().getExpiry();

                        scheduleNamedGraphExpiry(graphName, expiry);

                        final Long startTime = System.currentTimeMillis();
                        log.debug("Start put graph \"{}\" to cache.", graphName);

                        Futures.addCallback(putNamedGraphToCache(graphName, namedGraph), new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                log.debug("Successfully put graph \"{}\" to cache after {} millis.", graphName,
                                        System.currentTimeMillis() - startTime);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.error("Failed to put graph \"{}\" to cache after {} millis.",
                                        new Object[]{graphName, System.currentTimeMillis() - startTime, t});
                            }
                        });
                    }
                });
            }


//            else if (me.getMessage() instanceof SparqlQueryMessage) {
//                SparqlQueryMessage message = (SparqlQueryMessage) me.getMessage();
//                log.debug("Received SPARQL query: " + message.getQuery());
//
//                processSparqlQuery(message.getQueryResultFuture(), message.getQuery());
//            }


            else if(me.getMessage() instanceof DataOriginRemovalMessage){

                this.internalTasksExecutorService.execute(new Runnable(){

                    @Override
                    public void run() {
                        DataOriginRemovalMessage message = (DataOriginRemovalMessage) me.getMessage();
                        final URI graphName = message.getDataOrigin().getGraphName();

                        Futures.addCallback(deleteNamedGraph(graphName), new FutureCallback<Void>() {

                            @Override
                            public void onSuccess(Void result) {
                                log.info("Successfully deleted graph {} from cache!", graphName);

                                ScheduledFuture timeoutFuture = expiryFutures.remove(graphName);
                                if (timeoutFuture != null){
                                    if(timeoutFuture.cancel(false))
                                        log.info("Expiry for graph \"{}\" canceled.", graphName);
                                    else
                                        log.error("Failed to cancel expiry for graph \"{}\"!", graphName);
                                }
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.error("Failed to delete graph \"{}\" from cache!", graphName, t);
                            }

                        });
                    }
                });
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
            expiryFutures.put(graphName, internalTasksExecutorService.schedule(new Runnable() {
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
     * Method to check whether a graph with a given name is contained in the cache. The returned future MUST be
     * set with <code>true</code> if the cache contains a graph with given name or <code>false</code> otherwise.
     *
     * If some error occurred it may alternatively set with an {@link java.lang.Exception}. This method is used to avoid
     * overwriting already contained graphs with the same name (the graph name is a unique identifier). In case of an
     * exception the framework behaves like the future was set with <code>true</code>.
     *
     * @param graphName the name of the graph to be looked up
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of the
     * operation
     */
    public abstract ListenableFuture<Boolean> containsNamedGraph(URI graphName);


    /**
     * Method to retrieve a named graph from the cache. The returned future must be set with an instance of
     * {@link eu.spitfire.ssp.server.messages.GraphStatusMessage} representing the result of the operation, i.e.
     * either a {@link eu.spitfire.ssp.server.messages.ExpiringGraphStatusMessage} or <code>null</code> if no
     * such graph is contained in the cache.
     *
     * @param graphName the name of the graph to be looked up
     *
     * @return the {@link Model} containing the graph with the given name or <code>null</code> if no such graph is
     * contained in the cache.
     */
    public abstract ListenableFuture<ExpiringGraphStatusMessage> getNamedGraph(URI graphName);


    /**
     * Method to put a named graph into the cache. The returned future MUST be set with <code>null</code> if
     * the operation was successful, i.e. the named graph was put or with an {@link java.lang.Exception} if the
     * graph could not be put into the cache for some reason.
     *
     * @param graphName the name of the graph to be deleted from the cache
     * @param namedGraph the {@link Model} containing the named graph to be put into the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} which is to be set with the result of the
     * put operation
     */
    public abstract ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph);


    /**
     * Method to delete a cached named graph from the cache. The returned future MUST be set with <code>null</code> if
     * the operation was successful, i.e. the named graph was deleted or with an {@link java.lang.Exception} if the
     * graph could not be deleted from the cache for some reason.
     *
     * @param graphName the name of the graph to be deleted from the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} which is to be set with the result of the
     * delete operation
     */
    public abstract ListenableFuture<Void> deleteNamedGraph(URI graphName);


    /**
     * Method to process SPAQRL queries. Inheriting classes of
     * {@link eu.spitfire.ssp.server.handler.cache.SemanticCache} should override this method in order to support
     * SPAQRL queries.
     *
     * @param sparqlQuery the {@link com.hp.hpl.jena.query.Query} to be processed.
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with an instance of
     * {@link eu.spitfire.ssp.server.messages.GraphStatusMessage}.
     */
    public ListenableFuture<GraphStatusMessage> processSparqlQuery(Query sparqlQuery) {
        SettableFuture<GraphStatusMessage> resultFuture = SettableFuture.create();
        resultFuture.set(new GraphStatusErrorMessage(HttpResponseStatus.NOT_IMPLEMENTED,
                "The semantic cache does not support execution of SPARQL queries!"));

        return resultFuture;
    }
}

