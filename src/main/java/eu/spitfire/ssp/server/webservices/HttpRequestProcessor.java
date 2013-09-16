package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Generic interface for webservices to be published on this backends server. It is not supposed to be used directly
 * to build a webservice. Classes should implement one of {@link DefaultHttpRequestProcessor} to provide
 * non-semantic webservices or {@link SemanticHttpRequestProcessor} to provide semantic webservices.
 *
 * @author Oliver Kleine
 */
public interface HttpRequestProcessor<E> {

    public void processHttpRequest(SettableFuture<E> settableFuture, HttpRequest httpRequest);

}
