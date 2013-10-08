package eu.spitfire.ssp.backends.generic.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 30.09.13
 * Time: 20:17
 * To change this template use File | Settings | File Templates.
 */
public class DataOriginAccessException extends Exception{

    private HttpResponseStatus httpResponseStatus;

    public DataOriginAccessException(HttpResponseStatus httpResponseStatus, String message){
        super(message);
        this.httpResponseStatus = httpResponseStatus;
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }
}
