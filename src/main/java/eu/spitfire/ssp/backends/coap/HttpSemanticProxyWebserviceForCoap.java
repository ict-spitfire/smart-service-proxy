//package eu.spitfire.ssp.backends.coap;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.SettableFuture;
//import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
//import de.uniluebeck.itm.ncoap.message.CoapRequest;
//import de.uniluebeck.itm.ncoap.message.MessageCode;
//import de.uniluebeck.itm.ncoap.message.MessageType;
//import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
//import eu.spitfire.ssp.backends.generic.DataOriginManager;
//import eu.spitfire.ssp.backends.generic.ProtocolConversion;
//import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
//import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
//import org.jboss.netty.handler.codec.http.HttpMethod;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.util.concurrent.ExecutorService;
//
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
//
///**
// * Created with IntelliJ IDEA.
// * User: olli
// * Date: 02.10.13
// * Time: 23:18
// * To change this template use File | Settings | File Templates.
// */
//public class HttpSemanticProxyWebserviceForCoap implements ProtocolConversion {
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private CoapBackendComponentFactory backendComponentFactory;
//    private DataOriginManager<URI> dataOriginManager;
//    private CoapClientApplication coapClientApplication;
//    private ExecutorService internalTasksExecutorService;
//
//
//    public HttpSemanticProxyWebserviceForCoap(CoapBackendComponentFactory backendComponentFactory){
//        this.backendComponentFactory = backendComponentFactory;
//        this.coapClientApplication = backendComponentFactory.getCoapClientApplication();
//        this.dataOriginManager = backendComponentFactory.getDataOriginManager();
//        this.internalTasksExecutorService = backendComponentFactory.getInternalTasksExecutor();
//    }
//
//
//    @Override
//    public ListenableFuture<InternalResourceStatusMessage> processHttpRequest(final HttpRequest httpRequest) {
//
//        try{
//            URI resourceProxyUri = new URI(httpRequest.getUri());
//
//            if(resourceProxyUri.getQuery() == null || !(resourceProxyUri.getQuery().startsWith("uri="))){
//                String message = "Missing or malformed query parameter (\"uri=\"): " + resourceProxyUri.getQuery();
//                throw new DataOriginAccessException(BAD_GATEWAY, message);
//            }
//
//
//            final URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
//            log.info("Resource URI is {}", resourceUri);
//
//            //Find data origin for the resource
//            URI dataOrigin = dataOriginManager.getDataOrigin(resourceUri);
//
//            if(dataOrigin == null || !("coap".equals(dataOrigin.getScheme()))){
//                String message = "Invalid data origin for " + resourceUri + ": " + dataOrigin;
//                throw new DataOriginAccessException(INTERNAL_SERVER_ERROR, message);
//            }
//
//            //Create CoAP request
//            CoapRequest coapRequest = convertToCoapRequest(httpRequest, dataOrigin);
//            coapRequest.setAccept(ContentFormat.APP_RDF_XML, ContentFormat.APP_N3, ContentFormat.APP_TURTLE);
//
//            CoapWebserviceResponseProcessor responseProcessor = new CoapWebserviceResponseProcessor(
//                    this.backendComponentFactory, dataOrigin, resourceUri);
//
//            InetSocketAddress coapServerAddress = new InetSocketAddress(resourceUri.getHost(), resourceUri.getPort());
//
//            coapClientApplication.sendCoapRequest(coapRequest, responseProcessor, coapServerAddress);
//
//            return responseProcessor.getResourceStatusFuture();
//
//        }
//
//        catch (Exception e) {
//            String message = "Exception while converting from HTTP to CoAP request!";
//            log.error(message, e);
//
//            SettableFuture<InternalResourceStatusMessage> result = SettableFuture.create();
//            result.setException(new DataOriginAccessException(INTERNAL_SERVER_ERROR, message));
//
//            return result;
//        }
//    }
//
//    private static CoapRequest convertToCoapRequest(HttpRequest httpRequest, URI dataOrigin)
//            throws IllegalArgumentException{
//
//        //convert method
//        MessageCode.Name messageCode;
//        HttpMethod method = httpRequest.getMethod();
//
//        if (method.equals(HttpMethod.GET))
//            messageCode = MessageCode.Name.GET;
//
//        else if(method.equals(HttpMethod.DELETE))
//            messageCode = MessageCode.Name.DELETE;
//
//        else if(method.equals(HttpMethod.PUT))
//            messageCode = MessageCode.Name.PUT;
//
//        else if(method.equals(HttpMethod.POST))
//            messageCode = MessageCode.Name.POST;
//
//        else
//            throw new IllegalArgumentException("HTTP method " + method + " is not available in CoAP!");
//
//        CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, messageCode, dataOrigin);
//
//        if(messageCode == MessageCode.Name.POST || messageCode == MessageCode.Name.PUT){
//            if(httpRequest.getContent().readableBytes() > 0)
//                coapRequest.setContent(httpRequest.getContent());
//        }
//
//        coapRequest.setAccept(ContentFormat.APP_RDF_XML);
//        coapRequest.setAccept(ContentFormat.APP_N3);
//        coapRequest.setAccept(ContentFormat.APP_TURTLE);
//
//        return coapRequest;
//    }
//}
