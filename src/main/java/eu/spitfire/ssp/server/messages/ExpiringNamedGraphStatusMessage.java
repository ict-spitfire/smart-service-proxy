package eu.spitfire.ssp.server.messages;

import eu.spitfire.ssp.backends.generic.ExpiringNamedGraph;
import eu.spitfire.ssp.server.handler.cache.ExpiringGraph;

import java.net.URI;

/**
* Created by olli on 11.04.14.
*/
public class ExpiringNamedGraphStatusMessage extends ExpiringGraphStatusMessage {

    public ExpiringNamedGraphStatusMessage(StatusCode statusCode, ExpiringNamedGraph namedGraphStatus) {
        super(statusCode, namedGraphStatus);
    }


    @Override
    public ExpiringNamedGraph getExpiringGraph() {
        return (ExpiringNamedGraph) super.getExpiringGraph();
    }
}
