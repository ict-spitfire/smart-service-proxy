package eu.spitfire.ssp.backends.coap.requestprocessing;

import de.uniluebeck.itm.ncoap.message.CoapRequest;
import eu.spitfire.ssp.backends.utils.BackendManager;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * An instance of {@link HttpRequestProcessorForCoapWebservices} provides all functionality to handle
 * incoming {@link HttpRequest}s which is to convert them to a {@link CoapRequest}, send it to
 * the CoAP service host, await the response and convert the response to a {@link eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage}.
 *
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForCoapWebservices extends SemanticHttpRequestProcessor<URI> {

    private static Logger log = LoggerFactory.getLogger(HttpRequestProcessorForCoapWebservices.class.getName());

    public HttpRequestProcessorForCoapWebservices(BackendManager<URI> backendManager) {
        super(backendManager);
    }

}
