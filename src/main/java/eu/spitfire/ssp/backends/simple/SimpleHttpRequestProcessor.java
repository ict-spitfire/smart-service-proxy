//package eu.spitfire.ssp.backends.simple;
//
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.rdf.model.Model;
//import eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage;
//import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.URI;
//import java.util.Date;
//
///**
// * The {@link SimpleHttpRequestProcessor} is the {@link SemanticHttpRequestProcessor} instance to handle
// * incoming HTTP requests for the simple example resource (<code>http://example.org/JohnSmith</code>.
// *
// * @author Oliver Kleine
// */
//public class SimpleHttpRequestProcessor implements SemanticHttpRequestProcessor {
//
//    /**
//     * The time in milliseconds a status may be cached after a request.
//     */
//    public static long LIFETIME_MILLIS = 5000;
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//    private Model model;
//    private URI resourceUri;
//
//    /**
//     * @param model the {@link Model} that represents John Smith
//     *
//     * @throws Exception if some error occurred (this should actually never happen!)
//     */
//    public SimpleHttpRequestProcessor(Model model) throws Exception{
//        this.model = model;
//        resourceUri = new URI(model.listSubjects().next().toString());
//    }
//
//    @Override
//    public void processHttpRequest(SettableFuture<ResourceResponseMessage> responseFuture,
//                                   HttpRequest httpRequest) {
//
//        log.debug("Received request for path {}.", httpRequest.getUri());
//
//        //Set response
//        Date date = new Date(System.currentTimeMillis() + LIFETIME_MILLIS);
//        ResourceResponseMessage resourceStatusMessage =
//                    new ResourceResponseMessage(resourceUri, model, date);
//        responseFuture.set(resourceStatusMessage);
//    }
//}
