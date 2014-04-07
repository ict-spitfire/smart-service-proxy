package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Interface to implemented by HTTP Webservices offered by the Smart Service Proxy. Do NOT(!) implement this
 * interface directly but implement either {@link eu.spitfire.ssp.server.webservices.HttpNonSemanticWebservice} or
 * {@link eu.spitfire.ssp.backends.generic.HttpSemanticWebservice}
 *
 * @param <T> The type of the awaited response
 */
public interface HttpWebservice<T> {

    public void processHttpRequest(SettableFuture<T> responseFuture, HttpRequest httpRequest);

}
