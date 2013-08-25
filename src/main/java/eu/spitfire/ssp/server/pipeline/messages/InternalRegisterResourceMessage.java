package eu.spitfire.ssp.server.pipeline.messages;

import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import java.net.URI;

/**
 * This is an internal message to register new resources. Usually there is no need to directly instanciate or access
 * instances of this class.
 *
 * @author Oliver Kleine
 */
public class InternalRegisterResourceMessage {

    private URI resourceProxyUri;
    private HttpRequestProcessor httpRequestProcessor;

    /**
     * @param resourceProxyUri the {@link URI} at which the resource is reachable via the proxyservicemanagement
     * @param httpRequestProcessor the {@link HttpRequestProcessor} to process incoming requests to the resourceProxyUri
     */
    public InternalRegisterResourceMessage(URI resourceProxyUri, HttpRequestProcessor httpRequestProcessor) {
        this.resourceProxyUri = resourceProxyUri;
        this.httpRequestProcessor = httpRequestProcessor;
    }

    /**
     * Returns the {@link URI} at which the resource is reachable via the proxyservicemanagement
     * @return the {@link URI} at which the resource is reachable via the proxyservicemanagement
     */
    public URI getResourceProxyUri() {
        return resourceProxyUri;
    }

    /**
     * Returns the {@link HttpRequestProcessor} responsible to process incoming requests to the resourceProxyUri
     * @return the {@link HttpRequestProcessor} responsible to process incoming requests to the resourceProxyUri
     */
    public HttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    @Override
    public String toString(){
        return "[Resource registration] " +  resourceProxyUri + " (proxy uri)";
    }
}
