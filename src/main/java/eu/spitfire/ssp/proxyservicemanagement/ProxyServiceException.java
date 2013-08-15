package eu.spitfire.ssp.proxyservicemanagement;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 08.08.13
 * Time: 15:24
 * To change this template use File | Settings | File Templates.
 */
public class ProxyServiceException extends Exception{

    //private HttpVersion httpResponseVersion;
    private HttpResponseStatus httpResponseStatus;

    public ProxyServiceException(HttpResponseStatus httpResponseStatus){
        this(httpResponseStatus, httpResponseStatus.toString());
    }

    public ProxyServiceException(HttpResponseStatus httpResponseStatus,
                                 String message){
        super(message);
        //this.httpResponseVersion = httpResponseVersion;
        this.httpResponseStatus = httpResponseStatus;
    }

    public ProxyServiceException(HttpResponseStatus httpResponseStatus,
                                 String message, Throwable cause){
        this(httpResponseStatus, message);
        this.initCause(cause);
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

//    public HttpVersion getHttpResponseVersion() {
//        return httpResponseVersion;
//    }
}
