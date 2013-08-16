package eu.spitfire.ssp.proxyservicemanagement;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 08.08.13
 * Time: 15:24
 * To change this template use File | Settings | File Templates.
 */
public class ProxyServiceException extends Exception{

    private HttpResponseStatus httpResponseStatus;
    private URI resourceUri;

    public ProxyServiceException(URI resourceUri, HttpResponseStatus httpResponseStatus){
        this(resourceUri, httpResponseStatus, httpResponseStatus.toString());
    }

    public ProxyServiceException(URI resourceUri, HttpResponseStatus httpResponseStatus,
                                 String message){
        super(message);
        this.resourceUri = resourceUri;
        this.httpResponseStatus = httpResponseStatus;
    }

    public ProxyServiceException(URI resourceUri, HttpResponseStatus httpResponseStatus,
                                 String message, Throwable cause){
        this(resourceUri, httpResponseStatus, message);
        this.initCause(cause);
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public URI getResourceUri() {
        return resourceUri;
    }
}
