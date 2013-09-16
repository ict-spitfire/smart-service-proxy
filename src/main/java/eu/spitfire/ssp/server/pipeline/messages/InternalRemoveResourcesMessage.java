package eu.spitfire.ssp.server.pipeline.messages;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 14.08.13
 * Time: 16:34
 * To change this template use File | Settings | File Templates.
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
