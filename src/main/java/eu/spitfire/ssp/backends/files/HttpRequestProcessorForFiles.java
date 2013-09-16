package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.backends.ProxyServiceException;
import eu.spitfire.ssp.backends.coap.ResourceToolBox;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The instance of {@link HttpRequestProcessorForFiles} is just to inform the client that something went wrong.
 * As local files are observed by a {@link FilesObserver} the cache should always contain valid states of all resources
 * contained in the files.
 *
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForFiles implements SemanticHttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Map<URI, Path> resources = new HashMap<>();

    @Override
    public void processHttpRequest(SettableFuture<ResourceStatusMessage> resourceStatusFuture,
                                   HttpRequest httpRequest) {

        log.info("Received request for resource {}", httpRequest.getUri());
        if(httpRequest.getMethod() == HttpMethod.GET){
            processGET(resourceStatusFuture, httpRequest);
        }
        else if (httpRequest.getMethod() == HttpMethod.PUT){
            processPUT(resourceStatusFuture, httpRequest);
        }
    }

    private void processPUT(SettableFuture<ResourceStatusMessage> resourceStatusFuture,
                            HttpRequest httpRequest){
        try{
            URI resourceProxyUri = new URI(httpRequest.getUri());
            URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));

            //Read model from file
            Path resourceFile = resources.get(resourceUri);
            Model modelFromFile = ResourceToolBox.readModelFromFile(resourceFile);

            //Delete the resource from the model
            Resource resource = modelFromFile.getResource(resourceUri.toString());
            StmtIterator iterator = resource.listProperties();
            modelFromFile.remove(iterator);

            //Add the new resource status into the model
            Model newModel = modelFromFile.union(ResourceToolBox.getModelFromHttpMessage(httpRequest));

            //Write new status to the file
            FileWriter fileWriter = new FileWriter(resourceFile.toFile());
            newModel.write(fileWriter, Language.RDF_N3.lang);
            fileWriter.flush();
            fileWriter.close();


            resourceStatusFuture.setException(new ProxyServiceException(resourceUri, HttpResponseStatus.OK,
                    "File " + resourceFile + " succesfully updated."));

        } catch (URISyntaxException e) {
            log.error("This should never happen!", e);
            resourceStatusFuture.setException(new Exception("Something went really wrong!"));
        } catch (ProxyServiceException e) {
            log.error("Error while updating status of resource", e.getResourceUri());
            resourceStatusFuture.setException(e);
        } catch (IOException e) {
            log.error("Error while writing new status to file!", e);
            resourceStatusFuture.setException(e);
        }

    }

    private void processGET(SettableFuture<ResourceStatusMessage> resourceStatusFuture,
                            HttpRequest httpRequest){
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

    public void removeResource(URI resourceURI){
        resources.remove(resourceURI);
    }

    public void addResource(URI resourceURI, Path resourceFile){
        resources.put(resourceURI, resourceFile);
    }
}
