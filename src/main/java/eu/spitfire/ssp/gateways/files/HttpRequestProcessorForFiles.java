package eu.spitfire.ssp.gateways.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.gateways.ProxyServiceException;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The instance of {@link HttpRequestProcessorForFiles} is just to inform the client that something went wrong.
 * As local files are observed by a {@link FilesObserver} the cache should always contain valid states of all resources
 * contained in the files.
 *
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForFiles implements SemanticHttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Override
    public void processHttpRequest(SettableFuture<ResourceStatusMessage> resourceStatusFuture,
                                   HttpRequest httpRequest) {

        log.error("Received request for resource {}", httpRequest.getUri());
        try{
            URI resourceProxyUri = new URI(httpRequest.getUri());
            URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));

            String message = "The service at " + httpRequest.getUri() + " is backed by a local file and thus" +
                    "should be answered from the local cache!";
            ProxyServiceException exception =
                    new ProxyServiceException(resourceUri, HttpResponseStatus.INTERNAL_SERVER_ERROR, message);

            resourceStatusFuture.setException(exception);
        }
        catch(URISyntaxException e){
            log.error("This should never happen!", e);
            resourceStatusFuture.setException(new Exception("Something went really wrong!"));
        }
    }
}
