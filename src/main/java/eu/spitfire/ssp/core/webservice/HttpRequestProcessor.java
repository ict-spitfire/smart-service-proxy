package eu.spitfire.ssp.core.webservice;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.05.13
 * Time: 14:13
 * To change this template use File | Settings | File Templates.
 */
public interface HttpRequestProcessor<E> {

    public void processHttpRequest(SettableFuture<E> responseFuture, HttpRequest httpRequest);

}
