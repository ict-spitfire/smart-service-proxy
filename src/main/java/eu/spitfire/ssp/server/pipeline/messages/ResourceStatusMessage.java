package eu.spitfire.ssp.server.pipeline.messages;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

/**
 * Instances of {@link ResourceStatusMessage} contain the {@link URI} to identify the resource, a {@link Model}
 * containing the status of the resource and a {@link Date} to define the expiry of the actual status.
 *
 * @author Oliver Kleine
 */
public class ResourceStatusMessage {

    private static Logger log = LoggerFactory.getLogger(ResourceStatusMessage.class.getName());

    private final HttpResponseStatus httpResponseStatus;
    private final Resource resource;
    private final Date expiry;

    public ResourceStatusMessage(HttpResponseStatus httpResponseStatus){
        this(httpResponseStatus, null, null);
    }

    public ResourceStatusMessage(Resource resource, Date expiry){
        this(null, resource, expiry);
    }

    public ResourceStatusMessage(HttpResponseStatus httpResponseStatus, Resource resource, Date expiry){
        this.httpResponseStatus = httpResponseStatus;
        this.resource = resource;
        this.expiry = expiry;
    }

    /**
     * Returns the {@link Model} containing the actual status of the resource
     * @return the {@link Model} containing the actual status of the resource
     */
    public Resource getResource() {
        return this.resource;
    }

    /**
     * Returns the expiry of the actual status
     * @return the expiry of the actual status
     */
    public Date getExpiry() {
        return this.expiry;
    }

    /**
     * Returns the {@link URI} identifying the resource
     * @return the {@link URI} identifying the resource
     */
    public URI getResourceUri() {
        try {
            return new URI(resource.getURI());
        }
        catch (URISyntaxException e) {
            log.error("This should never happen.", e);
            return null;
        }
    }

    @Override
    public String toString(){
        return "[Resource status message] " + getResourceUri() + " (URI), " + getExpiry() + " (expiry)";
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }
}
