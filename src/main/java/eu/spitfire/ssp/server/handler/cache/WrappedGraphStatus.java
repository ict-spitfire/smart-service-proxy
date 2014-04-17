package eu.spitfire.ssp.server.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;

import java.util.Date;

/**
 * Created by olli on 17.04.14.
 */
public class WrappedGraphStatus {

    private Model status;
    private Date expiry;


    public WrappedGraphStatus(Model status, Date expiry) {
        this.status = status;
        this.expiry = expiry;
    }


    public Model getStatus() {
        return status;
    }


    public Date getExpiry() {
        return expiry;
    }
}
