package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.BackendComponentFactory;
import eu.spitfire.ssp.backends.DataOriginResponseMessage;
import eu.spitfire.ssp.backends.ResourceStatusHandler;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 *
 * @author Oliver Kleine
 */
public abstract class SemanticHttpRequestProcessor<T> extends ResourceStatusHandler<T>
        implements HttpRequestProcessor<ResourceStatusMessage> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public SemanticHttpRequestProcessor(BackendComponentFactory<T> backendComponentFactory){
        super(backendComponentFactory);
    }

    @Override
    public void processHttpRequest(final SettableFuture<ResourceStatusMessage> resourceResponseFuture,
                                   HttpRequest httpRequest){
        try {
            final URI resourceUri = new URI(new URI(httpRequest.getUri()).getQuery().substring(4));
            log.info("Received request for resource {}", resourceUri);

            //Retrieve data origin for the requested resource
            final T dataOrigin = this.backendComponentFactory.getDataOrigin(resourceUri);

            //Let data origin accessory perform the operation on the data origin
            final SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture = SettableFuture.create();
            this.backendComponentFactory.getDataOriginAccessory()
                               .processHttpRequest(dataOriginResponseFuture, httpRequest, dataOrigin);

            //Convert data origin response to resource response message
            dataOriginResponseFuture.addListener(new Runnable(){
                @Override
                public void run() {
                    ResourceStatusMessage resourceStatusMessage;
                    try {
                        Model modelFromDataOrigin = dataOriginResponseFuture.get().getModel();

                        if(modelFromDataOrigin == null){
                             resourceStatusMessage =
                                    new ResourceStatusMessage(dataOriginResponseFuture.get().getHttpResponseStatus());
                        }
                        else{
                            Resource resource = modelFromDataOrigin.getResource(resourceUri.toString());
                            Model resourceModel = ModelFactory.createDefaultModel();

                            StmtIterator stmtIterator = resource.listProperties();
                            while(stmtIterator.hasNext()){
                                Statement statement = stmtIterator.nextStatement();
                                resourceModel.add(statement);
                            }

                            resourceStatusMessage =
                                    new ResourceStatusMessage(dataOriginResponseFuture.get().getHttpResponseStatus(),
                                                                resourceModel.getResource(resourceUri.toString()),
                                                                dataOriginResponseFuture.get().getExpiry());
                        }

                        resourceResponseFuture.set(resourceStatusMessage);
                    }
                    catch (Exception e) {
                        log.error("Error while getting response from data origin {}", dataOrigin , e);
                        resourceResponseFuture.setException(e);
                    }
                }
            }, backendComponentFactory.getScheduledExecutorService());
        }
        catch (URISyntaxException e){
            log.error("This should never happen.", e);
            resourceResponseFuture.setException(e);
        }
    }
}
