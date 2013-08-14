package eu.spitfire.ssp.gateway.simple;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.vocabulary.VCARD;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.core.pipeline.handler.cache.ResourceStatusMessage;
import eu.spitfire.ssp.core.webservice.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.gateway.ProxyServiceException;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 13:29
 * To change this template use File | Settings | File Templates.
 */
public class SimpleHttpRequestProcessor implements SemanticHttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private Model model = null;

    public void setAboutUri(URI aboutURI){
        model = ModelFactory.createDefaultModel();
        model.createResource(aboutURI.toString()).addProperty(VCARD.FN, "John Smith");
    }

    @Override
    public void processHttpRequest(SettableFuture<ResourceStatusMessage> responseFuture,
                                   HttpRequest httpRequest) {

        log.debug("Received request for path {}.", httpRequest.getUri());

        while(model == null){};

        //Send response
        try {
            Date date = new Date(System.currentTimeMillis() + 5000);
            URI resourceUri = new URI("http", null, Main.SSP_DNS_NAME, Main.SSP_HTTP_PROXY_PORT,
                    httpRequest.getUri(), null, null);

            ResourceStatusMessage resourceStatusMessage =
                    new ResourceStatusMessage(resourceUri, model, date);

            responseFuture.set(resourceStatusMessage);
        } catch (URISyntaxException e) {
            String message = "Error with URI creation of simple resource";
            log.error(message, e);
            responseFuture.setException(new ProxyServiceException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    message, e));
            return;
        }
    }
}
