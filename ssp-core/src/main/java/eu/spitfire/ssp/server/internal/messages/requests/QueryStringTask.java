package eu.spitfire.ssp.server.internal.messages.requests;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;

/**
 * Created by olli on 18.06.15.
 */
public class QueryStringTask {

    private String query;
    private SettableFuture<ResultSet> resultSetFuture;

    /**
     * Creates a new instance of {@link QueryTask}
     * @param query the {@link com.hp.hpl.jena.query.Query} to be executed
     * @param resultSetFuture the {@link com.google.common.util.concurrent.SettableFuture} which
     *                        is set with the result of the query execution.
     */
    public QueryStringTask(String query, SettableFuture<ResultSet> resultSetFuture) {
        this.query = query;
        this.resultSetFuture = resultSetFuture;
    }

    /**
     * Returns the query to be executed.
     * @return the query to be executed.
     */
    public String getQuery() {
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
