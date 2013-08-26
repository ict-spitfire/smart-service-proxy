package eu.spitfire.ssp.proxyservicemanagement.simple;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
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
    private Model model;
    private URI resourceUri;

    public SimpleHttpRequestProcessor(Model model) throws Exception{
        this.model = model;
        resourceUri = new URI(model.listSubjects().next().toString());
    }

    @Override
    public void processHttpRequest(SettableFuture<ResourceStatusMessage> responseFuture,
                                   HttpRequest httpRequest) {

        log.debug("Received request for path {}.", httpRequest.getUri());

        //Send response
        Date date = new Date(System.currentTimeMillis() + 5000);
        ResourceStatusMessage resourceStatusMessage =
                    new ResourceStatusMessage(resourceUri, model, date);
        responseFuture.set(resourceStatusMessage);
    }
}
