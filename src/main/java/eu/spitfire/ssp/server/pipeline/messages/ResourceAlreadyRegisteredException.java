package eu.spitfire.ssp.server.pipeline.messages;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 18:50
 * To change this template use File | Settings | File Templates.
 */
public class ResourceAlreadyRegisteredException extends Exception {

    private URI resourceProxyUri;

    public ResourceAlreadyRegisteredException(URI resourceProxyUri){
        this.resourceProxyUri = resourceProxyUri;
    }

    public URI getResourceProxyUri() {
        return resourceProxyUri;
    }
}
