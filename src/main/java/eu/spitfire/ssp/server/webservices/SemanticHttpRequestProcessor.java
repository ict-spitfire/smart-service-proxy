package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.utils.*;
import eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 08.08.13
 * Time: 14:54
 * To change this template use File | Settings | File Templates.
 */
public abstract class SemanticHttpRequestProcessor<T> implements HttpRequestProcessor<ResourceResponseMessage> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private BackendManager<T> backendManager;

    public SemanticHttpRequestProcessor(BackendManager<T> backendManager){
        this.backendManager = backendManager;
    }

    @Override
    public final void processHttpRequest(final SettableFuture<ResourceResponseMessage> resourceResponseFuture,
                                   HttpRequest httpRequest){
        try {
            final URI resourceUri = new URI(new URI(httpRequest.getUri()).getQuery().substring(4));
            log.info("Received request for resource {}", resourceUri);

            //Retrieve data origin for the requested resource
            final T dataOrigin = this.backendManager.getDataOrigin(resourceUri);

            //Let data origin accessory perform the operation on the data origin
            final SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture = SettableFuture.create();
            this.backendManager.getDataOriginAccessory()
                               .processHttpRequest(dataOriginResponseFuture, httpRequest, dataOrigin);

            //Convert data origin response to resource response message
            dataOriginResponseFuture.addListener(new Runnable(){

                @Override
                public void run() {
                    ResourceResponseMessage resourceResponseMessage;
                    try {
                        Model modelFromDataOrigin = dataOriginResponseFuture.get().getModel();

                        if(modelFromDataOrigin == null){
                             resourceResponseMessage =
                                    new ResourceResponseMessage(dataOriginResponseFuture.get().getHttpResponseStatus());
                        }
                        else{
                            Resource resource = modelFromDataOrigin.getResource(resourceUri.toString());
                            Model resourceModel = ModelFactory.createDefaultModel();

                            StmtIterator stmtIterator = resource.listProperties();
                            while(stmtIterator.hasNext()){
                                Statement statement = stmtIterator.nextStatement();
                                resourceModel.add(statement);
                            }

                            resourceResponseMessage =
                                    new ResourceResponseMessage(dataOriginResponseFuture.get().getHttpResponseStatus(),
                                                                resourceModel.getResource(resourceUri.toString()),
                                                                dataOriginResponseFuture.get().getExpiry());
                        }

                        resourceResponseFuture.set(resourceResponseMessage);
                    }
                    catch (Exception e) {
                        log.error("Error while getting response from data origin {}", dataOrigin , e);
                        resourceResponseFuture.setException(e);
                    }
                }
            }, backendManager.getScheduledExecutorService());
        }
        catch (URISyntaxException e){
            log.error("This should never happen.", e);
            resourceResponseFuture.setException(e);
        }
    }
}
