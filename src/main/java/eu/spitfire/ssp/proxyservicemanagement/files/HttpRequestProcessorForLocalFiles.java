package eu.spitfire.ssp.proxyservicemanagement.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.MethodNotAllowedException;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 23.08.13
 * Time: 17:35
 * To change this template use File | Settings | File Templates.
 */
public class HttpRequestProcessorForLocalFiles implements SemanticHttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public void processHttpRequest(SettableFuture<ResourceStatusMessage> responseFuture, HttpRequest httpRequest) {
        log.info("Received request for resource {}", httpRequest.getUri());

        responseFuture.setException(new MethodNotAllowedException(httpRequest.getMethod()));
    }
}
