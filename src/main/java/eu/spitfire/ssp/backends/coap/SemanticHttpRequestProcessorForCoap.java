package eu.spitfire.ssp.backends.coap;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.backends.generic.BackendResourceManager;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.backends.generic.exceptions.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.MethodNotAllowedException;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutorService;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 23:18
 * To change this template use File | Settings | File Templates.
 */
public class SemanticHttpRequestProcessorForCoap implements SemanticHttpRequestProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapBackendComponentFactory backendComponentFactory;
    private BackendResourceManager<URI> backendResourceManager;
    private CoapClientApplication coapClientApplication;
    private ExecutorService executorService;


    public SemanticHttpRequestProcessorForCoap(CoapBackendComponentFactory backendComponentFactory){
        this.backendComponentFactory = backendComponentFactory;
        this.coapClientApplication = backendComponentFactory.getCoapClientApplication();
        this.backendResourceManager = backendComponentFactory.getBackendResourceManager();
        this.executorService = backendComponentFactory.getScheduledExecutorService();
    }

    @Override
    public void processHttpRequest(final SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
                                   final HttpRequest httpRequest) {
        try{
            URI resourceProxyUri = new URI(httpRequest.getUri());

            if(resourceProxyUri.getQuery() == null || !(resourceProxyUri.getQuery().startsWith("uri="))){
                String message = "Missing or malformed query parameter (\"uri=\"): " + resourceProxyUri.getQuery();
                throw new DataOriginAccessException(BAD_GATEWAY, message);
            }


            final URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
            log.info("Resource URI is {}", resourceUri);

            //Find data origin for the resource
            URI dataOrigin = backendResourceManager.getDataOrigin(resourceUri);

            if(dataOrigin == null || !("coap".equals(dataOrigin.getScheme()))){
                String message = "Invalid data origin for " + resourceUri + ": " + dataOrigin;
                throw new DataOriginAccessException(INTERNAL_SERVER_ERROR, message);
            }

            //Create CoAP request
            CoapRequest coapRequest = convertToCoapRequest(httpRequest, dataOrigin);
            coapRequest.setAccept(APP_SHDT, APP_RDF_XML, APP_N3, APP_TURTLE);

            coapClientApplication.writeCoapRequest(coapRequest,
                    new CoapWebserviceResponseProcessor(backendComponentFactory, resourceStatusFuture, dataOrigin,
                            resourceUri));

        }
        catch (Exception e) {
            String message = "Exception while converting from HTTP to CoAP request!";
            log.error(message, e);
            resourceStatusFuture.setException(new DataOriginAccessException(INTERNAL_SERVER_ERROR, message));
        }
    }

    private static CoapRequest convertToCoapRequest(HttpRequest httpRequest, URI dataOrigin) throws Exception{
        //convert method
        Code code;
        HttpMethod method = httpRequest.getMethod();

        if (method.equals(HttpMethod.GET))
            code = Code.GET;
        else if(method.equals(HttpMethod.DELETE))
            code = Code.DELETE;
        else if(method.equals(HttpMethod.PUT))
            code = Code.PUT;
        else if(method.equals(HttpMethod.POST))
            code = Code.POST;
        else
            throw new MethodNotAllowedException(httpRequest.getMethod());

        CoapRequest coapRequest = new CoapRequest(MsgType.CON, code, dataOrigin);

        if(code == Code.POST || code == Code.PUT){
            if(httpRequest.getContent().readableBytes() > 0)
                coapRequest.setPayload(httpRequest.getContent());
        }

        coapRequest.setAccept(APP_SHDT);
        coapRequest.setAccept(APP_RDF_XML);
        coapRequest.setAccept(APP_N3);
        coapRequest.setAccept(APP_TURTLE);

        return coapRequest;
    }
}
