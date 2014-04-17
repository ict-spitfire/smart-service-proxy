package eu.spitfire.ssp.server.messages;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.handler.cache.WrappedGraphStatus;

import java.util.Date;

/**
 * Created by olli on 17.04.14.
 */
public class GraphStatusMessage {

    public static enum Code{
        OK, CHANGED, DELETED
    }

    private Code code;
    private Model status;
    private Date expiry;

    public GraphStatusMessage(Code code, WrappedGraphStatus graphStatus) {
        this.code = code;
        this.status = graphStatus.getStatus();
        this.expiry = graphStatus.getExpiry();
    }


    public Code getCode() {
        return code;
    }

    public Model getStatus() {
        return status;
    }

    public Date getExpiry() {
        return expiry;
    }

}
