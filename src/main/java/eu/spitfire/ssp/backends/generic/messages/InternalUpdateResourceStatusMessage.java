package eu.spitfire.ssp.backends.generic.messages;

import com.hp.hpl.jena.rdf.model.Statement;

import java.net.URI;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 21:56
 * To change this template use File | Settings | File Templates.
 */
public class InternalUpdateResourceStatusMessage {

    private final Statement statement;
    private final Date expiry;

    public InternalUpdateResourceStatusMessage(Statement statement, Date expiry){
        this.statement = statement;
        this.expiry = expiry;
    }

    public Statement getStatement() {
        return statement;
    }

    public Date getExpiry() {
        return expiry;
    }
}
