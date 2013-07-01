package eu.spitfire.ssp.core.httpServer.webServices;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.05.13
 * Time: 14:13
 * To change this template use File | Settings | File Templates.
 */
public interface HttpRequestProcessor {

    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest);

}
