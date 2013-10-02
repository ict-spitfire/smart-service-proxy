package eu.spitfire.ssp.backends.generic.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;

import java.net.URI;

/**
 * Exception to be thrown by implementations of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory} or its sub-components whenever
 * something went wrong during the processing of an incoming {@link HttpRequest}.
 *
 * To be more precise, instances
 * of this exception must be set on the given {@link SettableFuture} on method invocation of
 * {@link HttpRequestProcessor#processHttpRequest(SettableFuture, HttpRequest)}.
 *
 * Upon setting such an exception, the SSP will send a proper {@link HttpResponse} to the client
 *
 * @author Oliver Kleine
 */
public class SemanticResourceException extends Exception{

    private HttpResponseStatus httpResponseStatus;
    private URI resourceUri;

//    /**
//     * @param resourceUri the {@link URI} of the resource that caused the exception.
//     * @param httpResponseStatus the {@link HttpResponseStatus} to be set on the {@link HttpResponse}.
//     */
//    public SemanticResourceException(URI resourceUri, HttpResponseStatus httpResponseStatus){
//        this(resourceUri, httpResponseStatus, httpResponseStatus.toString());
//    }

    /**
     * @param resourceUri the {@link URI} of the resource that caused the exception.
     * @param httpResponseStatus the {@link HttpResponseStatus} to be set on the {@link HttpResponse}.
     * @param message a {@łink String} containing additional messages to included in the payload of the response.
     */
    public SemanticResourceException(URI resourceUri, HttpResponseStatus httpResponseStatus, String message){
        super(message);
        this.resourceUri = resourceUri;
        this.httpResponseStatus = httpResponseStatus;
    }

    /**
     * @param resourceUri the {@link URI} of the resource that caused the exception.
     * @param httpResponseStatus the {@link HttpResponseStatus} to be set on the {@link HttpResponse}.
     * @param message a {@łink String} containing additional messages to included in the payload of the response.
     * @param cause the {@link Throwable} that caused this exception. The stacktrace will be included in the
     *              payload of the HTTP response.
     */
    public SemanticResourceException(URI resourceUri, HttpResponseStatus httpResponseStatus, String message,
                                     Throwable cause){
        this(resourceUri, httpResponseStatus, message);
        this.initCause(cause);
    }

    /**
     * Returns the {@link HttpResponseStatus} to be set on the HTTP response
     * @return the {@link HttpResponseStatus} to be set on the HTTP response
     */
    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    /**
     * Returns the {@link URI} of the resource that caused the exception
     * @return the {@link URI} of the resource that caused the exception
     */
    public URI getResourceUri() {
        return resourceUri;
    }
}
