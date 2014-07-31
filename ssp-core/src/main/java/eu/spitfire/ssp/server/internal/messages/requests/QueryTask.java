package eu.spitfire.ssp.server.internal.messages.requests;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;

/**
 * Wrapper class to combine a {@link com.hp.hpl.jena.query.Query} with a
 * {@link com.google.common.util.concurrent.SettableFuture} which contains the result of the
 * query after its execution.
 *
 * @author Oliver Kleine
 */
public class QueryTask {

    private Query query;
    private SettableFuture<ResultSet> resultSetFuture;

    /**
     * Creates a new instance of {@link QueryTask}
     * @param query the {@link com.hp.hpl.jena.query.Query} to be executed
     * @param resultSetFuture the {@link com.google.common.util.concurrent.SettableFuture} which
     *                        is set with the result of the query execution.
     */
    public QueryTask(Query query, SettableFuture<ResultSet> resultSetFuture) {
        this.query = query;
        this.resultSetFuture = resultSetFuture;
    }

    /**
     * Returns the {@link com.hp.hpl.jena.query.Query} to be executed.
     * @return the {@link com.hp.hpl.jena.query.Query} to be executed.
     */
    public Query getQuery() {
        return query;
    }

    /**
     * Returns the {@link com.google.common.util.concurrent.SettableFuture} to be set with the result of the query
     * execution.
     * @return the {@link com.google.common.util.concurrent.SettableFuture} to be set with the result of the query
     * execution.
     */
    public SettableFuture<ResultSet> getResultSetFuture() {
        return resultSetFuture;
    }
}
