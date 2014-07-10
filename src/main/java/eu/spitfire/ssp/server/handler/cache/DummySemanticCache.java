package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.internal.messages.responses.QueryResult;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A very simple implementation of a semantic cache to provide cached states of semantic resources. The caching
 * just happens in a {@link HashMap} mapping {@link URI}s to {@link Model}s.
 *
 * After expiry the states are deleted from the map automatically, such that requests after expiry cannot be answered
 * with a state from the cache.
 *
 * @author Oliver Kleine
 */
public class DummySemanticCache extends SemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DummySemanticCache(ExecutorService ioExecutorService, ScheduledExecutorService scheduledExecutorService) {
        super(ioExecutorService, scheduledExecutorService);
    }

    @Override
    public ListenableFuture<ExpiringGraph> getNamedGraph(URI graphName) {

        SettableFuture<ExpiringGraph> resultFuture = SettableFuture.create();
        resultFuture.set(null);

        return resultFuture;
    }

    @Override
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {

        SettableFuture<Boolean> resultFuture = SettableFuture.create();
        resultFuture.set(false);

        return resultFuture;
    }

    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, Model namedGraph) {
        SettableFuture<Void> resultFuture = SettableFuture.create();
        resultFuture.set(null);

        return resultFuture;
    }

    @Override
    public ListenableFuture<Void> deleteNamedGraph(URI graphName) {
        SettableFuture<Void> resultFuture = SettableFuture.create();
        resultFuture.set(null);

        return resultFuture;
    }

    @Override
    public ListenableFuture<QueryResult> processSparqlQuery(Query sparqlQuery) {
        SettableFuture<QueryResult> resultFuture = SettableFuture.create();
        resultFuture.set(null);

        return resultFuture;
    }

//    @Override
//    public void updateStatement(Statement statement) {
//        //Nothing to do...
//    }

//    @Override
//    public boolean supportsSPARQL() {
//        return false;
//    }
}
