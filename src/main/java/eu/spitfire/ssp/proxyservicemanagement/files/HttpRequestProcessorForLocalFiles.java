package eu.spitfire.ssp.proxyservicemanagement.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.DefaultHttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.MethodNotAllowedException;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 23.08.13
 * Time: 17:35
 * To change this template use File | Settings | File Templates.
 */
public class HttpRequestProcessorForLocalFiles implements DefaultHttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    //Map<URI, Path> fileResources;

//    public HttpRequestProcessorForLocalFiles(){
//        fileResources = new HashMap<>();
//    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        log.error("Received request for resource {}", httpRequest.getUri());
        String message = "The service at " + httpRequest.getUri() + " is backed by a local file and thus" +
                "should be answered from the local cache!";

        HttpResponse httpResponse = HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
        responseFuture.set(httpResponse);
    }

//    public void addFileResource(URI resourceUri, Path absolutePath){
//        fileResources.put(resourceUri, absolutePath);
//    }
//
//    public void removeFileResource(URI resourceUri){
//        fileResources.remove(resourceUri);
//    }
}
