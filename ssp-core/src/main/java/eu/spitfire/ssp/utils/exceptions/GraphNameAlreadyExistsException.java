package eu.spitfire.ssp.utils.exceptions;

import java.net.URI;

/**
 * Created by olli on 10.04.14.
 */
public class GraphNameAlreadyExistsException extends Exception{

    private URI graphName;


    public GraphNameAlreadyExistsException(URI graphName) {
        super("Graph with backendName " + graphName + " does already exist!");
        this.graphName = graphName;
    }


    public URI getGraphName() {
        return graphName;
    }
}
