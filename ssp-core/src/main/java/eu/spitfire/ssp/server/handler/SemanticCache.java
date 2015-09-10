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
package eu.spitfire.ssp.server.handler;

import com.google.common.util.concurrent.*;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import eu.spitfire.ssp.server.internal.message.*;
import eu.spitfire.ssp.server.internal.utils.Converter;
import eu.spitfire.ssp.server.internal.utils.HttpResponseFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

//import eu.spitfire.ssp.server.internal.utils.exceptions.GraphNameAlreadyExistsException;

/**
 * Checks whether the incoming {@link HttpRequest} can be answered with cached information. This depends on the
 * existence of cached information and its age. If there is suitable information available, the request will be
 * answered by sending a corresponding Object of type @link{Model} to the downstream. Otherwise the request will
 * be send to the upstream unchanged to be processed by the {@link eu.spitfire.ssp.server.handler.HttpRequestDispatcher}.
 *
 * @author Oliver Kleine
 */
public abstract class SemanticCache extends SimpleChannelHandler {

    public static final int DELAY_AFTER_EXPIRY_MILLIS = 10000;
    private static Logger LOG = LoggerFactory.getLogger(SemanticCache.class.getName());
    private static final TimeUnit MILLIS = TimeUnit.MILLISECONDS;

    private Map<URI, ScheduledFuture> namedGraphExpiryFutures = Collections.synchronizedMap(new HashMap<>());

    private ListeningScheduledExecutorService internalTasksExecutor;
    private ExecutorService ioTasksExecutor;

    protected SemanticCache(ExecutorService ioTasksExecutor, ScheduledExecutorService internalTasksExecutor) {
        this.ioTasksExecutor = ioTasksExecutor;
        this.internalTasksExecutor = MoreExecutors.listeningDecorator(internalTasksExecutor);
    }


    protected ScheduledExecutorService getInternalTasksExecutor(){
        return this.internalTasksExecutor;
    }

    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh status of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh status is found, it sends this status (as an instance of {@link org.apache.jena.rdf.model.Model})
     * downstream to the {@link eu.spitfire.ssp.server.handler.HttpSemanticPayloadFormatter}.
     *
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me  The {@link MessageEvent} potentially containing the {@link HttpRequest}
     * @throws Exception in case of an error
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if (me.getMessage() instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) me.getMessage();

            if (httpRequest.getMethod() == HttpMethod.GET) {
                String uriQuery = new URI(httpRequest.getUri()).getQuery();

                if (uriQuery != null) {
                    String[] queryParts = uriQuery.split("&");
                    for (String queryPart : queryParts) {
                        if (queryPart.startsWith("graph=")) {
                            URI graphName = new URI(queryPart.substring(6).replace(" ", "%20"));
                            internalTasksExecutor.execute(new GraphRequestHandler(graphName, ctx, me));
                            return;
                        }
                        else if (queryPart.startsWith("resource=")) {
                            URI resourceName = new URI(queryPart.substring(9));
                            internalTasksExecutor.execute(new ResourceRequestHandler(resourceName, ctx, me));
                            return;
                        }
                    }
                }
            }
        }

        ctx.sendUpstream(me);
    }



    private class ResourceRequestHandler implements Runnable{

        private URI resourceName;
        private ChannelHandlerContext ctx;
        private InetSocketAddress remoteAdress;
        private HttpVersion httpVersion;

        private ResourceRequestHandler(URI resourceName, ChannelHandlerContext ctx, MessageEvent me){
            this.resourceName = resourceName;
            this.ctx = ctx;
            this.httpVersion = ((HttpRequest) me.getMessage()).getProtocolVersion();
            this.remoteAdress = (InetSocketAddress) me.getRemoteAddress();
        }

        @Override
        public void run() {
            try {
                Query query = QueryFactory.create("SELECT ?p ?o WHERE {<" + resourceName + "> ?p ?o .}");

                Futures.addCallback(processSparqlQuery(query), new FutureCallback<QueryExecutionResults>() {

                    @Override
                    public void onSuccess(QueryExecutionResults results) {
                        ResultSet resultSet = results.getResultSet();
                        LOG.debug("Result for lookup resource \"{}\": {}", resourceName, resultSet);

                        Model model = Converter.toModel(resultSet, resourceName.toString());

                        //Send expiring graph
                        ExpiringGraph expiringGraph = new ExpiringGraph(model, new Date());
                        ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                        Channels.write(ctx, channelFuture, expiringGraph, remoteAdress);
                        channelFuture.addListener(ChannelFutureListener.CLOSE);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                        ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                        Channels.write(ctx, channelFuture, httpResponse, remoteAdress);

                        channelFuture.addListener(ChannelFutureListener.CLOSE);
                    }

                }, ioTasksExecutor);
            }
            catch (Exception ex) {
                ioTasksExecutor.execute(new Runnable(){

                    @Override
                    public void run() {
                        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

                        ChannelFuture channelFuture = Channels.future(ctx.getChannel());
                        Channels.write(ctx, channelFuture, httpResponse, remoteAdress);

                        channelFuture.addListener(ChannelFutureListener.CLOSE);
                    }
                });
            }
        }
    }


    private class GraphRequestHandler implements Runnable{

        private URI graphName;
        private final ChannelHandlerContext ctx;
        private final MessageEvent me;

        private GraphRequestHandler(URI graphName, ChannelHandlerContext ctx, MessageEvent me){
            this.graphName = graphName;
            this.ctx = ctx;
            this.me = me;
        }

        @Override
        public void run() {
            LOG.debug("Lookup graph \"{}\".", graphName);
            Futures.addCallback(getNamedGraph(graphName), new FutureCallback<ExpiringGraph>() {

                @Override
                public void onSuccess(ExpiringGraph expiringGraph) {
                    if (expiringGraph == null) {
                        LOG.warn("Graph \"{}\" NOT FOUND in cache!", graphName);
                        ctx.sendUpstream(me);
                    } else {
                        ChannelFuture future = Channels.future(ctx.getChannel());
                        Channels.write(ctx, future, expiringGraph, me.getRemoteAddress());

                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Error while inquiring graph \"{}\" in cache!", graphName, t);
                    ctx.sendUpstream(me);
                }

            }, ioTasksExecutor);
        }
    }



    private void scheduleNamedGraphExpiry(final URI graphName, Date expiry) {
        LOG.info("Received new status of {} (expiry: {})", graphName, expiry);
        Long startTime = System.currentTimeMillis();
        LOG.debug("Schedule timeout for resource {}.", graphName);

        //Cancel old expiry (if existing)
        ScheduledFuture timeoutFuture = namedGraphExpiryFutures.remove(graphName);
        if (timeoutFuture != null)
            timeoutFuture.cancel(false);

        LOG.debug("Canceled timeout after {} millis for resource {}", System.currentTimeMillis() - startTime,
                graphName);

        //Set new expiry (if not null)
        if (expiry != null) {
            namedGraphExpiryFutures.put(graphName, internalTasksExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        deleteNamedGraph(graphName);
                    } catch (Exception e) {
                        LOG.error("Could not delete resource {} from cache.", graphName, e);
                    }
                }
            }, expiry.getTime() - System.currentTimeMillis() + DELAY_AFTER_EXPIRY_MILLIS, TimeUnit.MILLISECONDS));
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
     * {@link eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph} representing the result of the operation, i.e.
     * either a {@link eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph}.
     *
     * @param graphName the name of the graph to be looked up
     *
     * @return the {@link Model} containing the graph with the given name or <code>null</code> if no such graph is
     * contained in the cache.
     */
    public abstract ListenableFuture<ExpiringNamedGraph> getNamedGraph(URI graphName);


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


    public abstract ListenableFuture<Void> updateSensorValue(URI graphName, RDFNode sensorValue);

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
     * {@link SemanticCache} should override this method in order to support
     * SPAQRL queries.
     *
     * @param query the {@link org.apache.jena.query.Query} to be processed.
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with an instance of
     * {@link eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults}.
     */
    public abstract ListenableFuture<QueryExecutionResults> processSparqlQuery(Query query);

    @Override
    public void writeRequested(ChannelHandlerContext ctx, final MessageEvent me) {

        try {
            if(me.getMessage() instanceof DataOriginRegistrationRequest){
                DataOriginRegistrationRequest request = (DataOriginRegistrationRequest) me.getMessage();
                DataOriginRegistrationTask task = new DataOriginRegistrationTask(request);
                MoreExecutors.directExecutor().execute(task);
            }

            else if(me.getMessage() instanceof DataOriginDeregistrationRequest){
                DataOriginDeregistrationRequest request = (DataOriginDeregistrationRequest) me.getMessage();
                DataOriginDeregistrationTask task = new DataOriginDeregistrationTask(request);
                MoreExecutors.directExecutor().execute(task);
            }

            else if (me.getMessage() instanceof ExpiringNamedGraph) {
                ExpiringNamedGraph graph = (ExpiringNamedGraph) me.getMessage();
                PutExpiringNamedGraphToCacheTask task = new PutExpiringNamedGraphToCacheTask(graph);
                this.internalTasksExecutor.execute(task);
            }

            else if (me.getMessage() instanceof InternalCacheUpdateRequest){
                InternalCacheUpdateRequest request = (InternalCacheUpdateRequest) me.getMessage();
                PutExpiringNamedGraphToCacheTask task = new PutExpiringNamedGraphToCacheTask(
                        request.getExpiringNamedGraph(), request.getCacheUpdateFuture()
                );
                this.internalTasksExecutor.execute(task);
            }

            else if (me.getMessage() instanceof InternalQueryExecutionRequest) {
                QueryProcessingTask task = new QueryProcessingTask((InternalQueryExecutionRequest) me.getMessage());
                MoreExecutors.directExecutor().execute(task);
            }

            else if (me.getMessage() instanceof DataOriginReplacementRequest){
                DataOriginReplacementRequest request = (DataOriginReplacementRequest) me.getMessage();
                DataOriginDeletionTask task = new DataOriginDeletionTask(
                    request.getOldDataOrigin().getGraphName(), request.getReplacementFuture()
                );
                MoreExecutors.directExecutor().execute(task);
            }

            ctx.sendDownstream(me);
        }

        catch (Exception e) {
          LOG.error("Cache error! ", e);
          me.getFuture().setFailure(e);
        }
    }


    private class DataOriginDeletionTask implements Runnable {

        private URI graphName;
        private SettableFuture<Void> deletionFuture;


        private DataOriginDeletionTask(URI graphName, SettableFuture<Void> deletionFuture) {
            this.graphName = graphName;
            this.deletionFuture = deletionFuture;
        }


        @Override
        public void run() {
            Futures.addCallback(deleteNamedGraph(graphName), new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    deletionFuture.set(null);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    deletionFuture.setException(throwable);
                }
            });
        }


    }

    private class DataOriginRegistrationTask implements Runnable {

        private URI graphName;
        private Model initialGraph;
        private SettableFuture<?> registrationFuture;

        private DataOriginRegistrationTask(DataOriginRegistrationRequest registrationRequest) {
            this.graphName = registrationRequest.getDataOrigin().getGraphName();
            this.initialGraph = registrationRequest.getInitialStatus();
            this.registrationFuture = registrationRequest.getRegistrationFuture();
        }

        @Override
        public void run() {
            //Add new graph with initial status to cache
            ListenableFuture<Void> insertionFuture = putNamedGraphToCache(graphName, initialGraph);
            Futures.addCallback(insertionFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    LOG.debug("Initial graph \"{}\" added to cache!", graphName);
                    registrationFuture.set(null);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Could not add graph \"{}\" to cache!", graphName, throwable);
                    registrationFuture.setException(throwable);
                }

            }, internalTasksExecutor);
        }
    }


    private class DataOriginDeregistrationTask implements Runnable{

        private URI graphName;
        private SettableFuture<?> deregistrationFuture;

        private DataOriginDeregistrationTask(DataOriginDeregistrationRequest deregistrationRequest){
            this.graphName = deregistrationRequest.getDataOrigin().getGraphName();
            this.deregistrationFuture = deregistrationRequest.getDeregistrationFuture();
        }

        @Override
        public void run() {

            //Delete the named graph from cache
            final ListenableFuture<Void> deleteFuture = deleteNamedGraph(graphName);
            Futures.addCallback(deleteFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    LOG.info("Successfully deleted graph {} from cache!", graphName);

                    ScheduledFuture expiryFuture = namedGraphExpiryFutures.remove(graphName);
                    if (expiryFuture != null){
                        if(expiryFuture.cancel(false)){
                            LOG.debug("Expiry for graph \"{}\" canceled.", graphName);
                        }
                        else{
                            LOG.error("Failed to cancel expiry for graph \"{}\"!", graphName);
                        }
                    }

                    deregistrationFuture.set(null);
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Failed to delete graph \"{}\" from cache!", graphName, t);
                    deregistrationFuture.setException(t);
                }
            }, internalTasksExecutor);
        }
    }

    private class PutExpiringNamedGraphToCacheTask implements Runnable{

        private final URI graphName;
        private final Model graph;
        private final Date expiry;
        private final SettableFuture<Void> future;

        private PutExpiringNamedGraphToCacheTask(ExpiringNamedGraph graph){
            this(graph, null);
        }

        private PutExpiringNamedGraphToCacheTask(ExpiringNamedGraph graph, SettableFuture<Void> future){
            this.graphName = graph.getGraphName();
            this.graph = graph.getModel();
            this.expiry = graph.getExpiry();
            this.future = future;
        }

        @Override
        public void run() {

            //Update cache
            LOG.debug("Start put graph \"{}\" to cache.", graphName);
            ListenableFuture<Void> updateFuture = putNamedGraphToCache(graphName, graph);

            Futures.addCallback(updateFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    if(future != null){
                        future.set(null);
                    }
                    scheduleNamedGraphExpiry(graphName,  expiry);
                    LOG.info("Successfully put graph \"{}\" to cache ", graphName);
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Failed to put graph \"{}\" to cache!", graphName, t);
                }

            }, getInternalTasksExecutor());
        }
    }


    private class QueryProcessingTask implements Runnable{

        private Query query;
        private SettableFuture<QueryExecutionResults> resultsFuture;

        private QueryProcessingTask(InternalQueryExecutionRequest request){
            this.query = request.getQuery();
            this.resultsFuture = request.getResultsFuture();
        }

        @Override
        public void run() {
            LOG.debug("Received Query Request: " + query.toString(Syntax.syntaxSPARQL));
            Futures.addCallback(processSparqlQuery(query), new FutureCallback<QueryExecutionResults>() {

                @Override
                public void onSuccess(QueryExecutionResults results) {
                    resultsFuture.set(results);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    resultsFuture.setException(throwable);
                }
            }, MoreExecutors.directExecutor());
        }
    }
}

