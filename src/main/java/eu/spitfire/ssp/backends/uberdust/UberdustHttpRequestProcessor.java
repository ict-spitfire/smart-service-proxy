package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.BackendComponentFactory;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The {@link UberdustHttpRequestProcessor} is the {@link eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor} instance to handle
 * incoming HTTP requests for the simple example resource (<code>http://example.org/JohnSmith</code>.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustHttpRequestProcessor extends SemanticHttpRequestProcessor<URI> {
    /**
     * Logger.
     */
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * The time in milliseconds a status may be cached after a request.
     */
    public static long LIFETIME_MILLIS = 5000;

    /**
     * The observer containing the updated information from Uberdust.
     */
    private UberdustObserver uberdustObserver;
    /**
     * Executor used for communicating back to Uberdust.
     */
    private final ExecutorService executor;

    /**
     * @param backendComponentFactory
     * @param uberdustObserver        an instance of an {@link eu.spitfire.ssp.backends.uberdust.UberdustObserver} that retrieves updated measurements.
     * @throws Exception if some error occurred (this should actually never happen!)
     */
    public UberdustHttpRequestProcessor(BackendComponentFactory backendComponentFactory, UberdustObserver uberdustObserver) {
        super(backendComponentFactory);
        this.backendComponentFactory = backendComponentFactory;
        this.uberdustObserver = uberdustObserver;
        executor = Executors.newCachedThreadPool();
    }

//    @Override
//    public void processHttpRequest(SettableFuture<ResourceStatusMessage> responseFuture,
//                                   HttpRequest httpRequest) {
//
//        log.debug("Received request for path {}.", httpRequest.getUri());
//
//        if (httpRequest.getMethod() == HttpMethod.GET) {
//            //handle get
//            handleGet(responseFuture, httpRequest);
//
//        } else if (httpRequest.getMethod() == HttpMethod.POST) {
//            //handle post
//            handlePost(responseFuture, httpRequest);
//        } else {
//            //handle anything else with method_not_allowed
//            ProxyServiceException exception = null;
//            try {
//                exception = new ProxyServiceException(new URI(httpRequest.getUri()), HttpResponseStatus.METHOD_NOT_ALLOWED,
//                        httpRequest.getMethod().getName() + " Requests are not allowed to " + httpRequest.getUri());
//            } catch (URISyntaxException e) {
//                log.error(e.getMessage(), e);
//            }
//            responseFuture.setException(exception);
//        }
//    }

//    /**
//     * Handles an incoming get request.
//     *
//     * @param responseFuture the response to be sent back.
//     * @param httpRequest    the request from the client.
//     */
//    public void handleGet(SettableFuture<ResourceStatusMessage> responseFuture,
//                          HttpRequest httpRequest) {
//        try {
//
//            UberdustNode node = uberdustObserver.allnodes.get(new URI(httpRequest.getUri().substring(httpRequest.getUri().indexOf("=") + 1)));
//            //Set response
//            Date date = new Date(System.currentTimeMillis() + LIFETIME_MILLIS);
//            ResourceStatusMessage resourceStatusMessage =
//                    new ResourceStatusMessage(new URI(httpRequest.getUri()), node.getModel(), date);
//            responseFuture.set(resourceStatusMessage);
//
//        } catch (URISyntaxException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//    }

//    /**
//     * Handles an incoming post request.
//     *
//     * @param responseFuture the response to be sent back.
//     * @param httpRequest    the request from the client.
//     */
//    public void handlePost(SettableFuture<ResourceStatusMessage> responseFuture,
//                           HttpRequest httpRequest) {
//        URI resourceUri = null;
//        try {
//            resourceUri = new URI(httpRequest.getUri().substring(httpRequest.getUri().indexOf("=") + 1));
//
//            //read payload from HTTP message
//            byte[] httpPayload = new byte[httpRequest.getContent().readableBytes()];
//
//            httpRequest.getContent().getBytes(0, httpPayload);
//            ByteArrayOutputStream bin = new ByteArrayOutputStream();
//            bin.write(httpPayload);
//            bin.toString();
//
//            URL url = new URL(resourceUri.toString() + bin.toString() + "/");
//            log.info("Sending request to " + url.toString());
//            executor.submit(new UberdustPostRequest(url));
//
//            ProxyServiceException exception = new ProxyServiceException(resourceUri, HttpResponseStatus.OK,
//                    "Request Forwarded to " + resourceUri);
//            responseFuture.setException(exception);
//        } catch (IOException | URISyntaxException e) {
//            log.error(e.getMessage(), e);
//            ProxyServiceException exception = new ProxyServiceException(resourceUri, HttpResponseStatus.INTERNAL_SERVER_ERROR,
//                    e.getMessage());
//            responseFuture.setException(exception);
//        }
//    }
}