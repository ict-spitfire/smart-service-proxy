package eu.spitfire.ssp.server.common.messages;


import eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
* Created by olli on 11.04.14.
*/
public class ExpiringNamedGraphStatusMessage extends ExpiringGraphStatusMessage {

    public ExpiringNamedGraphStatusMessage(ExpiringNamedGraph namedGraphStatus) {
        super(HttpResponseStatus.OK, namedGraphStatus);
    }


    @Override
    public ExpiringNamedGraph getExpiringGraph() {
        return (ExpiringNamedGraph) super.getExpiringGraph();
    }

    @Override
    public String toString(){
        return "[Expiring Named Graph Status Message (HTTP Code: " + getStatusCode() + ", Graph Name: " +
                getExpiringGraph().getGraphName() + "]";
    }
}
