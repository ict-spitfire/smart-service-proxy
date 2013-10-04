package eu.spitfire.ssp.backends.coap;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.header.Code;
import eu.spitfire.ssp.backends.generic.exceptions.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;


public class CoapWebserviceResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private static Logger log = LoggerFactory.getLogger(CoapWebserviceResponseProcessor.class.getName());

    private SettableFuture<InternalResourceStatusMessage> resourceStatusFuture;
    private URI dataOrigin;
    private URI resourceUri;
    private ExecutorService executorService;
    private LocalServerChannel localServerChannel;

    public CoapWebserviceResponseProcessor(CoapBackendComponentFactory backendComponentFactory,
                                           SettableFuture<InternalResourceStatusMessage> resourceStatusFuture,
                                           URI dataOrigin, URI resourceUri){

        this.localServerChannel = backendComponentFactory.getLocalServerChannel();
        this.executorService = backendComponentFactory.getScheduledExecutorService();
        this.resourceStatusFuture = resourceStatusFuture;
        this.dataOrigin = dataOrigin;
        this.resourceUri = resourceUri;
    }

    /**
     * Sets a value to the {@link SettableFuture} given as constructor parameter. This value is either a
     * {@link eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage} which is generated from the incoming {@link CoapResponse} or an
     * {@link eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException} if some error occurred.
     *
     * If there was no content in the response from the CoAP Webservice but the response code indicates success,
     * the {@link SettableFuture} is set with null (for requests with method PUT, POST or DELETE ).
     *
     * @param coapResponse the response message
     */
    @Override
    public void processCoapResponse(final CoapResponse coapResponse) {
        executorService.submit(new Runnable(){
            @Override
            public void run() {
                log.info("Process CoAP response: {}", coapResponse);
                try{
                    if(coapResponse.getCode().isErrorMessage()){
                        Code code = coapResponse.getCode();
                        HttpResponseStatus httpResponseStatus = CoapCodeHttpStatusMapper.getHttpResponseStatus(code);
                        String message = "CoAP response code from " + dataOrigin + " was " + code;
                        throw new SemanticResourceException(dataOrigin, httpResponseStatus, message);
                    }

                    if(coapResponse.getPayload().readableBytes() > 0 && coapResponse.getContentType() == null){
                        String message = "CoAP response had no content type option.";
                        throw new SemanticResourceException(dataOrigin, INTERNAL_SERVER_ERROR, message);
                    }

                    if(coapResponse.getContentType() != null && coapResponse.getPayload().readableBytes() == 0){
                        String message = "CoAP response had content type option but no content";
                        throw new SemanticResourceException(dataOrigin, INTERNAL_SERVER_ERROR, message);
                    }

                    if(coapResponse.getPayload().readableBytes() > 0){
                        Model completeModel = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
                        Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);

                        Map<URI, Model> models = CoapResourceToolbox.getModelsPerSubject(completeModel);

                        Model model = models.get(resourceUri);
                        if(model == null){
                            String message = "Resource " + resourceUri + " not found at data origin " + dataOrigin;
                            throw new DataOriginAccessException(INTERNAL_SERVER_ERROR, message);
                        }

                        InternalResourceStatusMessage resourceStatusMessage =
                                new InternalResourceStatusMessage(model, expiry);
                        resourceStatusFuture.set(resourceStatusMessage);
                    }
                    else{
                        log.info("CoAP response from {} indicates success but has no payload", dataOrigin);

                        Model model = ModelFactory.createDefaultModel();

                        InternalResourceStatusMessage resourceStatusMessage = new InternalResourceStatusMessage(model);
                        resourceStatusFuture.set(resourceStatusMessage);
                    }
                }
                catch(Exception e){
                    log.error("Error while creating resource status message from CoAP response.", e);
                    resourceStatusFuture.setException(e);
                }
            }
        });
    }

    /**
     * Sets a {@link eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException} on the {@link SettableFuture} given as constructor parameter
     *
     * @param timeoutMessage the {@link InternalRetransmissionTimeoutMessage} containing some information
     *                       on the remote host and the sent request.
     */
    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        String exMessage = "No response received from " + dataOrigin + ".";
        log.warn(exMessage);
        resourceStatusFuture.setException(new DataOriginAccessException(GATEWAY_TIMEOUT, exMessage));

        InternalRemoveResourcesMessage message = new InternalRemoveResourcesMessage(resourceUri);
        Channels.write(localServerChannel, message);
    }
}
