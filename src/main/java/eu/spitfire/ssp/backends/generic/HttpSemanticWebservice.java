package eu.spitfire.ssp.backends.generic;

import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.HttpWebservice;

/**
 * Interface to be implemented by HTTP Webservice instances running on the Smart Service Proxy to
 * provide semantic data, i.e. semantic resource states.
 *
 * Implementing classes are supposed to process the incoming {@link org.jboss.netty.handler.codec.http.HttpRequest},
 * e.g. by converting it to another protocol, forward the translated request to a data-origin, await the
 * response, and set the given {@link com.google.common.util.concurrent.SettableFuture} with an instance of
 * {@link eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage} based on the response from
 * the data-origin.
 *
 * @author Oliver Kleine
 */
public interface HttpSemanticWebservice extends HttpWebservice<InternalResourceStatusMessage> {
}
