package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.handler.cache.ExpiringGraph;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 10.04.14.
 */
public class ExpiringNamedGraph extends ExpiringGraph {

    private URI graphName;

    public ExpiringNamedGraph(URI graphName, Model graph, Date expiry){
        super(graph, expiry);
        this.graphName = graphName;
    }

    public URI getGraphName () {
        return this.graphName;
    }
}
