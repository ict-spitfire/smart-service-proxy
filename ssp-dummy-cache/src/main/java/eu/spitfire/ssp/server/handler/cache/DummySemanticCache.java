package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.QueryExecutionResults;

import org.apache.jena.query.Query;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.resultset.ResultSetMem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A dummy implementation of {@link eu.spitfire.ssp.server.handler.SemanticCache} which actually does not
 * perform any caching but only extends the abstract class.
 *
 * This is supposed to be used for debugging purposes.
 *
 * @author Oliver Kleine
 */
public class DummySemanticCache extends SemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DummySemanticCache(ExecutorService ioExecutorService, ScheduledExecutorService scheduledExecutorService) {
        super(ioExecutorService, scheduledExecutorService);
    }

    @Override
    public ListenableFuture<ExpiringNamedGraph> getNamedGraph(URI graphName) {

        SettableFuture<ExpiringNamedGraph> resultFuture = SettableFuture.create();
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
    public ListenableFuture<Void> updateSensorValue(URI graphName, RDFNode sensorValue) {
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
    public ListenableFuture<QueryExecutionResults> processSparqlQuery(Query query) {
        SettableFuture<QueryExecutionResults> resultsFuture = SettableFuture.create();
        resultsFuture.set(new QueryExecutionResults(0, new ResultSetMem()));

        return resultsFuture;
    }

//    @Override
//    public ListenableFuture<QueryResult> processSparqlQuery(Query sparqlQuery) {
//        SettableFuture<QueryResult> resultFuture = SettableFuture.create();
//        resultFuture.set(new QueryResult(new ResultSetMem()));
//
//        return resultFuture;
//    }
}
