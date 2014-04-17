package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.handler.cache.WrappedGraphStatus;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 10.04.14.
 */
public class WrappedNamedGraphStatus extends WrappedGraphStatus{

    private URI graphName;

    public WrappedNamedGraphStatus(URI graphName, Model status, Date expiry){
        super(status, expiry);
        this.graphName = graphName;
    }

    public URI getGraphName () {
        return this.graphName;
    }
}
