package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.resultset.ResultSetMem;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
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
    public ListenableFuture<ResultSet> processSparqlQuery(Query query) {
        SettableFuture<ResultSet> resultFuture = SettableFuture.create();
        resultFuture.set(new ResultSetMem());

        return resultFuture;
    }

//    @Override
//    public ListenableFuture<QueryResult> processSparqlQuery(Query sparqlQuery) {
//        SettableFuture<QueryResult> resultFuture = SettableFuture.create();
//        resultFuture.set(new QueryResult(new ResultSetMem()));
//
//        return resultFuture;
//    }
}
