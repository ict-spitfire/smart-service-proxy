package eu.spitfire.ssp.backends.coap.requestprocessing;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.backends.ProxyServiceException;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.server.webservices.MethodNotAllowedException;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * An instance of {@link HttpRequestProcessorForCoapServices} provides all functionality to handle
 * incoming {@link HttpRequest}s which is to convert them to a {@link CoapRequest}, send it to
 * the CoAP service host, await the response and convert the response to a {@link ResourceStatusMessage}.
 *
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForCoapServices implements SemanticHttpRequestProcessor {

    private static Logger log = LoggerFactory.getLogger(HttpRequestProcessorForCoapServices.class.getName());

    //Map from resource URI to service URI (there might be several resources at one service)
    private Map<URI, URI> services;

    private CoapClientApplication coapClientApplication;

    /**
     * @param coapClientApplication the {@link CoapClientApplication} to send the {@link CoapRequest}s
     */
    public HttpRequestProcessorForCoapServices(CoapClientApplication coapClientApplication){
        this.coapClientApplication = coapClientApplication;
        this.services = Collections.synchronizedMap(new HashMap<URI, URI>());
    }

    public void addResource(URI resourceUri, URI serviceUri){
        services.put(resourceUri, serviceUri);
    }

    public void removeResource(URI resourceUri){
        services.remove(resourceUri);
    }

    @Override
    public void processHttpRequest(final SettableFuture<ResourceStatusMessage> resourceStatusFuture,
                                   final HttpRequest httpRequest) {
        try{
            URI resourceProxyUri = new URI(httpRequest.getUri());

            if(resourceProxyUri.getQuery() == null || !(resourceProxyUri.getQuery().startsWith("uri=coap://"))){
                resourceStatusFuture.setException(new ProxyServiceException(resourceProxyUri, BAD_GATEWAY,
                        "Requested URI scheme was either emoty or not coap."));
            }
            else{
                final URI resourceUri = new URI(resourceProxyUri.getQuery().substring(4));
                log.info("CoAP target URI: {}", resourceUri);

                //URI serviceUri = services.get(resourceUri);

                //Send CoAP request and wait for response
                CoapRequest coapRequest = convertToCoapRequest(httpRequest, resourceUri);
                log.info("Write CoAP request: {}", coapRequest);
                coapClientApplication.writeCoapRequest(coapRequest,
                        new CoapProxyResponseProcessor(resourceStatusFuture, resourceUri));
            }
        }
        catch (Exception e) {
            String message = "Exception while converting from HTTP to CoAP request!";
            log.error(message, e);
            resourceStatusFuture.setException(new ProxyServiceException(null, INTERNAL_SERVER_ERROR, message, e));
        }
    }


    private static CoapRequest convertToCoapRequest(HttpRequest httpRequest, URI resourceUri) throws Exception{
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

        CoapRequest coapRequest = new CoapRequest(MsgType.CON, code, resourceUri);

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
