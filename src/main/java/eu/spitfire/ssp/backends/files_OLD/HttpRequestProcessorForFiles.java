//package eu.spitfire.ssp.backends.files_OLD;
//
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.rdf.model.Model;
//import com.hp.hpl.jena.rdf.model.ModelFactory;
//import com.hp.hpl.jena.rdf.model.Resource;
//import com.hp.hpl.jena.rdf.model.StmtIterator;
//import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
//import eu.spitfire.ssp.backends.generic.ResourceToolbox;
//import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
//import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
//import org.jboss.netty.handler.codec.http.HttpMethod;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.jboss.netty.handler.codec.http.HttpResponseStatus;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.nio.file.Path;
//import java.util.HashMap;
//import java.util.Map;
//
///**
//* The instance of {@link HttpRequestProcessorForFiles} is just to inform the client that something went wrong.
//* As local files_OLD are observed by a {@link FilesObserver____OLD} the cache should always contain valid states of all resources
//* contained in the files_OLD.
//*
//* @author Oliver Kleine
//*/
//public class HttpRequestProcessorForFiles implements SemanticHttpRequestProcessor {
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private Map<URI, Path> resources = new HashMap<>();
//
//    @Override
//    public void processHttpRequest(SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
//                                   HttpRequest httpRequest) {
//
//        log.info("Received request for resource {}", httpRequest.getUri());
//        try{
//            if(httpRequest.getMethod() == HttpMethod.GET){
//                processGET(resourceStatusFuture, httpRequest);
//            }
//            else if (httpRequest.getMethod() == HttpMethod.POST){
//                processPOST(resourceStatusFuture, httpRequest);
//            }
//            else if (httpRequest.getMethod() == HttpMethod.PUT){
//                processPUT(resourceStatusFuture, httpRequest);
//            }
//        }
//        catch (Exception e) {
//            resourceStatusFuture.setException(e);
//        }
//
//    }
//
//    public void processPOST(SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
//                            HttpRequest httpRequest) throws Exception {
//
//        URI resourceProxyUri = new URI(httpRequest.getUri());
//        URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
//
//        SemanticResourceException exception = new SemanticResourceException(resourceUri,
//                HttpResponseStatus.METHOD_NOT_ALLOWED, "Method POST is not supported for resource " + resourceUri);
//
//        resourceStatusFuture.setException(exception);
//    }
//
//    private void processPUT(SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
//                            HttpRequest httpRequest) throws Exception {
//
//        URI resourceProxyUri = new URI(httpRequest.getUri());
//        URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
//
//        //Read model from file
//        Path resourceFile = resources.get(resourceUri);
//
//        synchronized (resourceFile){
//            Model modelFromFile = FilesResourceToolBox.readModelFromFile(resourceFile);
//
//            //Delete the resource from the model
//            Resource resource = modelFromFile.getResource(resourceUri.toString());
//            StmtIterator iterator = resource.listProperties();
//            modelFromFile.remove(iterator);
//
//            //Add the new resource status into the model
//            Model newModel = modelFromFile.union(ResourceToolbox.getModelFromHttpMessage(httpRequest));
//
//            FilesResourceToolBox.writeModelToFile(resourceFile, newModel);
//
//            resourceStatusFuture.set(new InternalResourceStatusMessage(ModelFactory.createDefaultModel()));
//        }
//    }
//
//    private void processGET(SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
//                            HttpRequest httpRequest){
//        try{
//            URI resourceProxyUri = new URI(httpRequest.getUri());
//            URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
//
//            String message = "The service at " + httpRequest.getUri() + " is backed by a local file and thus" +
//                    "should be answered from the local cache!";
//            SemanticResourceException exception =
//                    new SemanticResourceException(resourceUri, HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
//
//            resourceStatusFuture.setException(exception);
//        }
//        catch(URISyntaxException e){
//            log.error("This should never happen!", e);
//            resourceStatusFuture.setException(new Exception("Something went really wrong!"));
//        }
//    }
//
//    public void removeResource(URI resourceURI){
//        resources.remove(resourceURI);
//    }
//
//    public void addResource(URI resourceURI, Path resourceFile){
//        resources.put(resourceURI, resourceFile);
//    }
//}
