package eu.spitfire.ssp.server.common.messages;

import eu.spitfire.ssp.server.webservices.HttpWebservice;

import java.net.URI;

/**
 * This is an internal message to register new resources. Usually there is no need to directly instantiate or access
 * instances of this class.
 *
 * @author Oliver Kleine
 */
public class WebserviceRegistrationMessage {

    private URI localUri;
    private HttpWebservice httpWebservice;

    /**
     * @param localUri the {@link URI} of the resource, e.g. the path
     * @param httpWebservice the {@link eu.spitfire.ssp.server.webservices.HttpWebservice} to process incoming HTTP
     *                       requests
     */
    public WebserviceRegistrationMessage(URI localUri, HttpWebservice httpWebservice) {
        this.localUri = localUri;
        this.httpWebservice = httpWebservice;
    }

    /**
     * Returns the {@link URI} at which the resource is reachable via the backend
     * @return the {@link URI} at which the resource is reachable via the backend
     */
    public URI getLocalUri() {
        return localUri;
    }

    /**
     * Returns the {@link eu.spitfire.ssp.server.webservices.HttpWebservice} responsible to process incoming requests
     * to the localUri
     *
     * @return the {@link eu.spitfire.ssp.server.webservices.HttpWebservice} responsible to process incoming requests
     * to the localUri
     */
    public HttpWebservice getHttpWebservice() {
        return httpWebservice;
    }

    @Override
    public String toString(){
        return "[Proxy Webservice registration] " + localUri + " (proxy webservice uri)";
    }
}