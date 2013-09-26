package eu.spitfire.ssp.server.pipeline.messages;

import java.net.URI;

/**
 * Message to be written on the local server channel if a resource is to be removed from the list of registered
 * services and from the cache.
 *
 * @author Oliver Kleine
 */
public class InternalRemoveResourcesMessage {

    private URI resourceUri;

    public InternalRemoveResourcesMessage(URI resourceUri){
        this.resourceUri = resourceUri;
    }

    public URI getResourceUri() {
        return resourceUri;
    }

    public String toString(){
        return "[Remove resource message] " + resourceUri + " (uri)";
    }
}
