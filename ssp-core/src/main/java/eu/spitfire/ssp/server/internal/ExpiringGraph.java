package eu.spitfire.ssp.server.internal;



import org.apache.jena.rdf.model.Model;

import java.util.Date;

/**
 * Wrapper class to contain a {@link org.apache.jena.rdf.model.Model} and a {@link java.util.Date} indicating the
 * validity lifetime of the model.
 *
 * @author Oliver Kleine
 */
public class ExpiringGraph {

    /**
     * The number of milliseconds per century.
     */
    public static final long MILLIS_PER_CENTURY = 3155695200000L;

    private Model graph;
    private Date expiry;

    /**
     * Creates a new instance of {@link ExpiringGraph}
     * @param graph the actual graph, i.e. the model containing the actual triples.
     * @param expiry the expiry of the actual status
     */
    public ExpiringGraph(Model graph, Date expiry) {
        this.graph = graph;
        this.expiry = expiry;
    }

    /**
     * Creates a new instance of {@link ExpiringGraph}. The
     * expiry is automatically to the current time plus {@link #MILLIS_PER_CENTURY}, i.e. a hundred years in
     * the future.
     *
     * @param graph the actual graph, i.e. the model containing the actual triples.
     */
    public ExpiringGraph(Model graph){
        this(graph, new Date(System.currentTimeMillis() + MILLIS_PER_CENTURY));
    }

    /**
     * Returns the actual graph, i.e. the model containing the actual triples.
     * @return the actual graph, i.e. the model containing the actual triples.
     */
    public Model getModel() {
        return graph;
    }

    /**
     * Returns the expiry of the graph status
     * @return the expiry of the graph status
     */
    public Date getExpiry() {
        return expiry;
    }


    @Override
    public String toString() {
        return "[Expiring Graph (Expiry: " + this.getExpiry() + ")]";
    }
}
