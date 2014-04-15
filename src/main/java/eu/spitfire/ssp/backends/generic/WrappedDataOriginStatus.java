package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 10.04.14.
 */
public class WrappedDataOriginStatus {

    public enum Code{
        OK, CHANGED, DELETED;
    }

    private Code code;
    private URI graphName;
    private Model status;
    private Date expiry;


    public WrappedDataOriginStatus(Code code, URI graphName, Model status, Date expiry){
        this.code = code;
        this.graphName = graphName;
        this.status = status;
        this.expiry = expiry;
    }


    public Code getCode() {
        return this.code;
    }


    public Model getStatus() {
        return this.status;
    }


    public Date getExpiry() {
        return this.expiry;
    }


    public URI getGraphName () {
        return this.graphName;
    }
}
