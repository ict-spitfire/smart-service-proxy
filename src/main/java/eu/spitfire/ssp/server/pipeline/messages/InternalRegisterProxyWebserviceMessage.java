package eu.spitfire.ssp.server.pipeline.messages;

import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;

import java.net.URI;

/**
 * This is an internal message to register new resources. Usually there is no need to directly instantiate or access
 * instances of this class.
 *
 * @author Oliver Kleine
 */
public class InternalRegisterProxyWebserviceMessage {

    private URI proxyWebserviceUri;
    private HttpRequestProcessor httpRequestProcessor;

    /**
     * @param proxyWebserviceUri the {@link URI} at which the resource is reachable via the backends
     * @param httpRequestProcessor the {@link HttpRequestProcessor} to process incoming requests to the proxyWebserviceUri
     */
    public InternalRegisterProxyWebserviceMessage(URI proxyWebserviceUri, HttpRequestProcessor httpRequestProcessor) {
        this.proxyWebserviceUri = proxyWebserviceUri;
        this.httpRequestProcessor = httpRequestProcessor;
    }

    /**
     * Returns the {@link URI} at which the resource is reachable via the backend
     * @return the {@link URI} at which the resource is reachable via the backend
     */
    public URI getProxyWebserviceUri() {
        return proxyWebserviceUri;
    }

    /**
     * Returns the {@link HttpRequestProcessor} responsible to process incoming requests to the proxyWebserviceUri
     * @return the {@link HttpRequestProcessor} responsible to process incoming requests to the proxyWebserviceUri
     */
    public HttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    @Override
    public String toString(){
        return "[Proxy Webservice registration] " + proxyWebserviceUri + " (proxy webservice uri)";
    }
}
