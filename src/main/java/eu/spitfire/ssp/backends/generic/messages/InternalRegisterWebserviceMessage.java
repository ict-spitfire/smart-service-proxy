package eu.spitfire.ssp.backends.generic.messages;

import eu.spitfire.ssp.server.webservices.DefaultHttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;

import java.net.URI;

/**
 * This is an internal message to register new resources. Usually there is no need to directly instantiate or access
 * instances of this class.
 *
 * @author Oliver Kleine
 */
public class InternalRegisterWebserviceMessage {

    private URI relativeUri;
    private DefaultHttpRequestProcessor httpRequestProcessor;

    /**
     * @param relativeUri the {@link URI} at which the resource is reachable via the backends
     * @param httpRequestProcessor the {@link DefaultHttpRequestProcessor} to process incoming HTTP requests
     */
    public InternalRegisterWebserviceMessage(URI relativeUri, DefaultHttpRequestProcessor httpRequestProcessor) {
        this.relativeUri = relativeUri;
        this.httpRequestProcessor = httpRequestProcessor;
    }

    /**
     * Returns the {@link URI} at which the resource is reachable via the backend
     * @return the {@link URI} at which the resource is reachable via the backend
     */
    public URI getRelativeUri() {
        return relativeUri;
    }

    /**
     * Returns the {@link HttpRequestProcessor} responsible to process incoming requests to the relativeUri
     * @return the {@link HttpRequestProcessor} responsible to process incoming requests to the relativeUri
     */
    public DefaultHttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    @Override
    public String toString(){
        return "[Proxy Webservice registration] " + relativeUri + " (proxy webservice uri)";
    }
}
