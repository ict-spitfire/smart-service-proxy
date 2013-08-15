package eu.spitfire.ssp.gateway.coap.requestprocessing;

import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.core.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.core.webservice.MethodNotAllowedException;
import eu.spitfire.ssp.core.webservice.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.gateway.ProxyServiceException;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.APP_SHDT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForCoapServices implements SemanticHttpRequestProcessor {

    private static Logger log = LoggerFactory.getLogger(HttpRequestProcessorForCoapServices.class.getName());

    private CoapClientApplication coapClientApplication;

    @Inject
    public HttpRequestProcessorForCoapServices(CoapClientApplication coapClientApplication){
        this.coapClientApplication = coapClientApplication;
    }

    @Override
    public void processHttpRequest(final SettableFuture<ResourceStatusMessage> responseFuture,
                                   final HttpRequest httpRequest) {
        try{
            URI proxyUri = new URI(httpRequest.getUri());

            if(proxyUri.getQuery() == null || !(proxyUri.getQuery().startsWith("uri=coap://"))){
                responseFuture.setException(new ProxyServiceException(BAD_GATEWAY,
                        "Requested URI scheme was either emoty or not coap."));
                return;
            }

            final URI coapUri = new URI(proxyUri.getQuery().substring(4));
            log.debug("CoAP target URI: {}", coapUri);

            //Send CoAP request and wait for response
            CoapRequest coapRequest = convertToCoapRequest(httpRequest, coapUri);
            coapClientApplication.writeCoapRequest(coapRequest, new CoapResponseProcessor() {
                @Override
                public void processCoapResponse(CoapResponse coapResponse) {
                    log.debug("Process CoAP response: {}", coapResponse);

                    if(coapResponse.getCode().isErrorMessage()){
                        HttpResponseStatus httpResponseStatus =
                                CoapCodeHttpStatusMapper.getHttpResponseStatus(coapResponse.getCode());
                        responseFuture.setException(new ProxyServiceException(httpResponseStatus));
                        return;
                    }

                    if(coapResponse.getContentType() == null){
                        responseFuture.setException(new ProxyServiceException(INTERNAL_SERVER_ERROR,
                                "CoAP response had no content type option."));
                        return;
                    }

                    if(coapResponse.getContentType() != null && coapResponse.getPayload().readableBytes() == 0){
                        responseFuture.setException(new ProxyServiceException(INTERNAL_SERVER_ERROR,
                                "CoAP response had content type option but no content"));
                        return;
                    }

                    try{
                        ResourceStatusMessage resourceStatusMessage =
                                ResourceStatusMessage.create(coapResponse, coapUri);
                        responseFuture.set(resourceStatusMessage);
                    }
                    catch(Exception e){
                        log.error("Error while creating resource status message from CoAP response.", e);
                        responseFuture.setException(e);
                    }
                }
            });
            log.debug("CoAP request sent: {}", coapRequest);
        }
        catch (Exception e) {
            String message = "Exception while converting from HTTP to CoAP request!";
            log.error(message, e);
            responseFuture.setException(new ProxyServiceException(INTERNAL_SERVER_ERROR, message, e));
        }
    }


    private static CoapRequest convertToCoapRequest(HttpRequest httpRequest, URI targetURI) throws Exception{

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

        CoapRequest coapRequest = new CoapRequest(MsgType.CON, code, targetURI);

        if(code == Code.POST || code == Code.PUT){
            if(httpRequest.getContent().readableBytes() > 0)
                coapRequest.setPayload(httpRequest.getContent());
        }

        //TODO Set CoAP "Accept-Options" according to the HTTP "Accept-Header"

        coapRequest.setAccept(APP_SHDT);

        return coapRequest;
    }


}
