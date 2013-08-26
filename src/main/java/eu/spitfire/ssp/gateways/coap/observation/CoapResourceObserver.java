package eu.spitfire.ssp.gateways.coap.observation;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.observe.ObservationTimeoutProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.gateways.AbstractResourceObserver;
import eu.spitfire.ssp.gateways.coap.CoapProxyTools;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Instances of {@link CoapResourceObserver} are instances of {@link CoapResponseProcessor} to observe
 * CoAP webservices. If an update notification was received for an observed CoAP resource it sends
 * an {@link ResourceStatusMessage} to update the actual resource status in the cache.
 *
 * @author Oliver Kleine
 */
public class CoapResourceObserver extends AbstractResourceObserver implements CoapResponseProcessor,
        RetransmissionTimeoutProcessor, ObservationTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    private CoapRequest coapRequest;
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * @param coapRequest the {@link CoapRequest} to be sent if the observation times out (max-age expiry)
     * @param scheduledExecutorService the {@link ScheduledExecutorService} to run observation specific tasks
     * @param localChannel the {@link LocalServerChannel} to send internal messages, e.g. {@link ResourceStatusMessage}s
     *                     to update the cache.
     */
    public CoapResourceObserver(CoapRequest coapRequest, ScheduledExecutorService scheduledExecutorService,
                                LocalServerChannel localChannel){
        super(scheduledExecutorService, localChannel);

        this.coapRequest = coapRequest;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.info("Received response from service {}.", coapRequest.getTargetUri());

        if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){
            log.warn("Observation of {} failed. CoAP response was: {}", coapRequest.getTargetUri(), coapResponse);
            return;
        }

        //create resource status message to update the cache
        try {
            URI resourceUri = coapRequest.getTargetUri();
            Model resourceStatus = CoapProxyTools.getModelFromCoapResponse(coapResponse, coapRequest.getTargetUri());
            Date expiry = CoapProxyTools.getExpiryFromCoapResponse(coapResponse);
            cacheResourceStatus(resourceUri, resourceStatus, expiry);
        } catch (Exception e) {
            log.error("Exception while creating resource status message from CoAP update notification.", e);
            return;
        }
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                log.warn("Resource {} is unreachable. Remove from list of registered resoureces.",
                        coapRequest.getTargetUri());
                removeResourceStatusFromCache(coapRequest.getTargetUri());
            }
        }, 0, TimeUnit.SECONDS) ;
    }

    @Override
    public void processObservationTimeout(SettableFuture<CoapRequest> continueObservationFuture) {
        log.info("Max-Age of observed resource {} expired. Try to restart observation!", coapRequest.getTargetUri());
        continueObservationFuture.set(coapRequest);
    }


}
