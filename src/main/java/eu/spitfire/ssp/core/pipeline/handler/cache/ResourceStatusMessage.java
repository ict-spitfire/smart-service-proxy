package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 09.08.13
 * Time: 16:42
 * To change this template use File | Settings | File Templates.
 */
public class ResourceStatusMessage {

    private URI resourceUri;
    private final Model resourceStatus;
    private final Date expiry;

    public ResourceStatusMessage(URI resourceUri, Model resourceStatus, Date expiry){
        this.resourceUri = resourceUri;

        this.resourceStatus = resourceStatus;
        this.expiry = expiry;
    }

    public Model getResourceStatus() {
        return resourceStatus;
    }

    public Date getExpiry() {
        return expiry;
    }

    public URI getResourceUri() {
        return resourceUri;
    }
}
