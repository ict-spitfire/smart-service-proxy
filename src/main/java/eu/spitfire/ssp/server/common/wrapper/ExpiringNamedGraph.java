package eu.spitfire.ssp.server.common.wrapper;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Wrapper class to contain a {@link com.hp.hpl.jena.rdf.model.Model}, a {@link java.util.Date} indicating the
 * validity lifetime of the model, and the {@link java.net.URI} representing the name of the graph represented
 * by the model.
 *
 * @author Oliver Kleine
 */
public class ExpiringNamedGraph extends ExpiringGraph{

    private URI graphName;

    public ExpiringNamedGraph(URI graphName, Model graph, Date expiry) {
        super(graph, expiry);
        this.graphName = graphName;
    }


    public ExpiringNamedGraph(URI graphName, Model graph){
        this(graphName, graph, new Date(System.currentTimeMillis() + MILLIS_PER_YEAR));
    }

    public URI getGraphName() {
        return graphName;
    }
}
