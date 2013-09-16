package eu.spitfire.ssp.server.pipeline.messages;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.09.13
 * Time: 10:15
 * To change this template use File | Settings | File Templates.
 */
public class InternalSparqlQueryMessage {

    private String query;
    private SettableFuture<String> queryResultFuture;

    public InternalSparqlQueryMessage(String query, SettableFuture<String> queryResultFuture) {
        this.query = query;
        this.queryResultFuture = queryResultFuture;
    }

    public String getQuery() {
        return query;
    }

    public SettableFuture<String> getQueryResultFuture() {
        return queryResultFuture;
    }
}
