package eu.spitfire.ssp.core;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 14.08.13
 * Time: 16:34
 * To change this template use File | Settings | File Templates.
 */
public class InternalRemoveResourceMessage {

    private URI resourceUri;

    public InternalRemoveResourceMessage(URI resourceUri){

        this.resourceUri = resourceUri;
    }

    public URI getResourceUri() {
        return resourceUri;
    }
}
