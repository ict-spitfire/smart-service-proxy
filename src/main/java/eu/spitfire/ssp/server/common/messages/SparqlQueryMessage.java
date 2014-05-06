package eu.spitfire.ssp.server.common.messages;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;

/**
 * Created by olli on 06.05.14.
 */
public class SparqlQueryMessage {

    private Query query;
    private SettableFuture<ResultSet> queryResultFuture;

    public SparqlQueryMessage(Query query, SettableFuture<ResultSet> queryResultFuture) {
        this.query = query;
        this.queryResultFuture = queryResultFuture;
    }

    public Query getQuery() {
        return query;
    }

    public SettableFuture<ResultSet> getQueryResultFuture() {
        return queryResultFuture;
    }
}
