package eu.spitfire.ssp.server.messages;

import eu.spitfire.ssp.server.handler.cache.ExpiringGraph;

/**
 * Created by olli on 17.04.14.
 */
public class ExpiringGraphStatusMessage extends GraphStatusMessage{

    private ExpiringGraph expiringGraph;


    public ExpiringGraphStatusMessage(StatusCode statusCode, ExpiringGraph expiringGraph) {
        super(statusCode);
        this.expiringGraph = expiringGraph;
    }


    public ExpiringGraph getExpiringGraph() {
        return this.expiringGraph;
    }
}
