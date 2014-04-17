package eu.spitfire.ssp.server.messages;

import eu.spitfire.ssp.backends.generic.WrappedNamedGraphStatus;
import java.net.URI;

/**
* Created by olli on 11.04.14.
*/
public class NamedGraphStatusMessage extends GraphStatusMessage{

    private URI graphName;

    public NamedGraphStatusMessage(Code code, WrappedNamedGraphStatus namedGraphStatus) {
        super(code, namedGraphStatus);
        this.graphName = namedGraphStatus.getGraphName();

    }

    public URI getGraphName() {
        return this.graphName;
    }
}
