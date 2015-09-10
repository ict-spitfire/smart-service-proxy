package eu.spitfire.ssp.server.internal.wrapper;


import org.apache.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Wrapper class to contain a {@link org.apache.jena.rdf.model.Model}, a {@link java.util.Date} indicating the
 * validity lifetime of the model, and the {@link java.net.URI} representing the name of the graph represented
 * by the model.
 *
 * @author Oliver Kleine
 */
public class ExpiringNamedGraph extends ExpiringGraph{

    private URI graphName;

    /**
     * Creates a new instance of {@link ExpiringNamedGraph}.
     *
     * @param graphName the name of the contained graph
     * @param graph the actual graph, i.e. the model containing the actual triples.
     * @param expiry the expiry of the actual status
     */
    public ExpiringNamedGraph(URI graphName, Model graph, Date expiry) {
        super(graph, expiry);
        this.graphName = graphName;
    }

    /**
     * Creates a new instance of {@link ExpiringNamedGraph}. The
     * expiry is automatically to the current time plus {@link #MILLIS_PER_CENTURY}, i.e. a hundred years in
     * the future.
     *
     * @param graphName the actual graph, i.e. the model containing the actual triples.
     * @param graph the expiry of the actual status
     */
    public ExpiringNamedGraph(URI graphName, Model graph){
        this(graphName, graph, new Date(System.currentTimeMillis() + MILLIS_PER_CENTURY));
    }

    /**
     * Returns the name of the graph
     * @return the name of the graph
     */
    public URI getGraphName() {
        return graphName;
    }

    @Override
    public String toString(){
        return "[Expiring Named Graph (Graph Name: " + this.getGraphName() + ", Expiry: " + this.getExpiry() + ")].";
    }
}
