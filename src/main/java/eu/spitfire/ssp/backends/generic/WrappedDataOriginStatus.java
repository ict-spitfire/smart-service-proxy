package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 10.04.14.
 */
public class WrappedDataOriginStatus {

    private URI graphName;
    private Model status;
    private Date expiry;

    public WrappedDataOriginStatus(URI graphName, Model status, Date expiry){
        this.graphName = graphName;
        this.status = status;
        this.expiry = expiry;
    }


    public Model getStatus() {
        return status;
    }


    public Date getExpiry() {
        return expiry;
    }


    public URI getGraphName() {
        return graphName;
    }
}
