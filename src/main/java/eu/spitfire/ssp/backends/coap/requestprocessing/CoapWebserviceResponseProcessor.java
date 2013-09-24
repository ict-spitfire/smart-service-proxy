package eu.spitfire.ssp.backends.coap.requestprocessing;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.backends.utils.DataOriginException;
import eu.spitfire.ssp.backends.utils.DataOriginResponseMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;


public class CoapWebserviceResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private static Logger log = LoggerFactory.getLogger(CoapWebserviceResponseProcessor.class.getName());

    private SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture;
    private URI dataOrigin;

    /**
     * @param dataOriginResponseFuture the {@link SettableFuture} to contain the {@link DataOriginResponseMessage} upon
     *                                 processing the request at the data origin
     * @param dataOrigin the {@link URI} identifying the Webservice the {@link CoapResponse} comes from
     */
    public CoapWebserviceResponseProcessor(SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture,
                                           URI dataOrigin){
        this.dataOriginResponseFuture = dataOriginResponseFuture;
        this.dataOrigin = dataOrigin;
    }

    /**
     * Sets a value to the {@link SettableFuture} given as constructor parameter. This value is either a
     * {@link eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage} which is generated from the incoming {@link CoapResponse} or an
     * {@link eu.spitfire.ssp.backends.utils.DataOriginException} if some error occurred.
     *
     * If there was no content in the response from the CoAP Webservice but the response code indicates success,
     * the {@link SettableFuture} is set with null (for requests with method PUT, POST or DELETE ).
     *
     * @param coapResponse the response message
     */
    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.debug("Process CoAP response: {}", coapResponse);

        if(coapResponse.getCode().isErrorMessage()){
            HttpResponseStatus httpResponseStatus =
                    CoapCodeHttpStatusMapper.getHttpResponseStatus(coapResponse.getCode());
            dataOriginResponseFuture.setException(new DataOriginException(dataOrigin, httpResponseStatus));
            return;
        }

        if(coapResponse.getPayload().readableBytes() > 0 && coapResponse.getContentType() == null){
            dataOriginResponseFuture.setException(new DataOriginException(dataOrigin, INTERNAL_SERVER_ERROR,
                    "CoAP response had no content type option."));
            return;
        }

        if(coapResponse.getContentType() != null && coapResponse.getPayload().readableBytes() == 0){
            dataOriginResponseFuture.setException(new DataOriginException(dataOrigin, INTERNAL_SERVER_ERROR,
                    "CoAP response had content type option but no content"));
            return;
        }

        try{
            if(coapResponse.getPayload().readableBytes() > 0){
                Model model = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
                Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);

                DataOriginResponseMessage dataOriginResponseMessage =
                        new DataOriginResponseMessage(HttpResponseStatus.OK, model, expiry);
                dataOriginResponseFuture.set(dataOriginResponseMessage);
            }
            else{
                log.info("Update of resource {} was succesful.", dataOrigin);
                HttpResponseStatus httpResponseStatus =
                        CoapCodeHttpStatusMapper.getHttpResponseStatus(coapResponse.getCode());

                DataOriginResponseMessage dataOriginResponseMessage = new DataOriginResponseMessage(httpResponseStatus);
                dataOriginResponseFuture.set(dataOriginResponseMessage);
            }
        }
        catch(Exception e){
            log.error("Error while creating resource status message from CoAP response.", e);
            dataOriginResponseFuture.setException(e);
        }

    }

    /**
     * Sets a {@link eu.spitfire.ssp.backends.utils.DataOriginException} on the {@link SettableFuture} given as constructor parameter
     *
     * @param timeoutMessage the {@link InternalRetransmissionTimeoutMessage} containing some information
     *                       on the remote host and the sent request.
     */
    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        String message = "No response received from " + dataOrigin + ".";
        log.warn(message);
        dataOriginResponseFuture.setException(new DataOriginException(dataOrigin, GATEWAY_TIMEOUT, message));
    }
}
