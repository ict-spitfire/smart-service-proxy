package eu.spitfire.ssp.gateway;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 08.08.13
 * Time: 15:24
 * To change this template use File | Settings | File Templates.
 */
public class ProxyServiceException extends Exception{

    private HttpVersion httpResponseVersion;
    private HttpResponseStatus httpResponseStatus;

    public ProxyServiceException(HttpVersion httpResponseVersion, HttpResponseStatus httpResponseStatus){
        this(httpResponseVersion, httpResponseStatus, httpResponseStatus.toString());
    }

    public ProxyServiceException(HttpVersion httpResponseVersion, HttpResponseStatus httpResponseStatus,
                                 String message){
        super(message);
        this.httpResponseVersion = httpResponseVersion;
        this.httpResponseStatus = httpResponseStatus;
    }

    public ProxyServiceException(HttpVersion httpResponseVersion, HttpResponseStatus httpResponseStatus,
                                 String message, Throwable cause){
        this(httpResponseVersion, httpResponseStatus, message);
        this.initCause(cause);
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public HttpVersion getHttpResponseVersion() {
        return httpResponseVersion;
    }
}
