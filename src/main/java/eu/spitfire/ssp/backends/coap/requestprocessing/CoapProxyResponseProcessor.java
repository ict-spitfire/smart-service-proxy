package eu.spitfire.ssp.backends.coap.requestprocessing;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import eu.spitfire.ssp.backends.ProxyServiceException;
import eu.spitfire.ssp.backends.coap.ResourceToolBox;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

/**
 * A {@link CoapProxyResponseProcessor} provides awaits a {@link CoapResponse} and converts the content
 * to a proper {@link ResourceStatusMessage}.
 *
 * @author Oliver Kleine
 */
public class CoapProxyResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private Logger log = LoggerFactory.getLogger(CoapProxyResponseProcessor.class.getName());

    private SettableFuture<ResourceStatusMessage> resourceStatusFuture;
    private URI resourceUri;

    /**
     * @param resourceStatusFuture the {@link SettableFuture} to contain either the {@link ResourceStatusMessage} which is
     *                             generated from the incoming {@link CoapResponse} or an {@link ProxyServiceException} if some
     *                             error occured.
     * @param resourceUri the {@link URI} identifying the resource the {@link CoapResponse} comes from
     */
    public CoapProxyResponseProcessor(SettableFuture<ResourceStatusMessage> resourceStatusFuture, URI resourceUri){
        this.resourceStatusFuture = resourceStatusFuture;
        this.resourceUri = resourceUri;
    }

    /**
     * Sets a value to the {@link SettableFuture} given as constructor parameter. This value is either a
     * {@link ResourceStatusMessage} which is generated from the incoming {@link CoapResponse} or an
     * {@link ProxyServiceException} if some error occured.
     *
     * @param coapResponse the response message
     */
    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.debug("Process CoAP response: {}", coapResponse);

        if(coapResponse.getCode().isErrorMessage()){
            HttpResponseStatus httpResponseStatus =
                    CoapCodeHttpStatusMapper.getHttpResponseStatus(coapResponse.getCode());
            resourceStatusFuture.setException(new ProxyServiceException(resourceUri, httpResponseStatus));
            return;
        }

        if(coapResponse.getPayload().readableBytes() > 0 && coapResponse.getContentType() == null){
            resourceStatusFuture.setException(new ProxyServiceException(resourceUri, INTERNAL_SERVER_ERROR,
                    "CoAP response had no content type option."));
            return;
        }

        if(coapResponse.getContentType() != null && coapResponse.getPayload().readableBytes() == 0){
            resourceStatusFuture.setException(new ProxyServiceException(resourceUri, INTERNAL_SERVER_ERROR,
                    "CoAP response had content type option but no content"));
            return;
        }

        try{
            if(coapResponse.getPayload().readableBytes() > 0){
                Model model = ResourceToolBox.getModelFromCoapResponse(coapResponse);
                Map<URI, Model> resources = ResourceToolBox.getModelsPerSubject(model);

                Date expiry = ResourceToolBox.getExpiryFromCoapResponse(coapResponse);

                Model resource = resources.get(resourceUri);

                if(resource == null){
                    resourceStatusFuture.setException(new ProxyServiceException(resourceUri, NOT_FOUND,
                            "Requested resource " + resourceUri + " not found!"));
                }
                else{
                    ResourceStatusMessage resourceStatusMessage = new ResourceStatusMessage(resourceUri, resource, expiry);
                    resourceStatusFuture.set(resourceStatusMessage);
                }
            }
            else{
                String message = "Operation was successful. New Status is not included in this response";
                resourceStatusFuture.setException(new ProxyServiceException(resourceUri, HttpResponseStatus.OK,
                        message));
            }
        }
        catch(Exception e){
            log.error("Error while creating resource status message from CoAP response.", e);
            resourceStatusFuture.setException(e);
        }

    }

    /**
     * Sets a {@link ProxyServiceException} on the {@link SettableFuture} given as constructor parameter
     *
     * @param timeoutMessage the {@link InternalRetransmissionTimeoutMessage} containing some information
     *                       on the remote host and the sent request.
     */
    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        String message = "No response received from " + resourceUri + ".";
        log.warn(message);
        resourceStatusFuture.setException(new ProxyServiceException(resourceUri, GATEWAY_TIMEOUT, message));
    }
}
