package eu.spitfire.ssp.server.common.wrapper;

import com.hp.hpl.jena.rdf.model.Model;

import java.util.Date;

/**
 * Wrapper class to contain a {@link com.hp.hpl.jena.rdf.model.Model} and a {@link java.util.Date} indicating the
 * validity lifetime of the model.
 *
 * @author Oliver Kleine
 */
public class ExpiringGraph {

    public static final long MILLIS_PER_YEAR = 31556952000L;

    private Model graph;
    private Date expiry;


    public ExpiringGraph(Model graph, Date expiry) {
        this.graph = graph;
        this.expiry = expiry;
    }


    public ExpiringGraph(Model graph){
        this(graph, new Date(System.currentTimeMillis() + MILLIS_PER_YEAR));
    }

    public Model getGraph() {
        return graph;
    }


    public Date getExpiry() {
        return expiry;
    }
}
