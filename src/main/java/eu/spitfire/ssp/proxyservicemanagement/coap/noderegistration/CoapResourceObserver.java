package eu.spitfire.ssp.proxyservicemanagement.coap.noderegistration;

import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.proxyservicemanagement.AbstractResourceObserver;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * Instances of {@link CoapResourceObserver} are instances of {@link CoapResponseProcessor} to observe
 * CoAP webservices. If an update notification was received for an observed CoAP resource it sends
 * an {@link ResourceStatusMessage} to update the actual resource status in the cache.
 *
 * @author Oliver Kleine
 */
public class CoapResourceObserver extends AbstractResourceObserver implements CoapResponseProcessor,
        RetransmissionTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    private URI resourceUri;
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture scheduledFuture;

    /**
     * @param resourceUri the {@link URI} identifying the resource to be observed
     * @param scheduledExecutorService the {@link ScheduledExecutorService} to run observation specific tasks
     * @param localChannel the {@link LocalServerChannel} to send internal messages, e.g. {@link ResourceStatusMessage}s
     *                     to update the cache.
     */
    public CoapResourceObserver(URI resourceUri, ScheduledExecutorService scheduledExecutorService,
                                LocalServerChannel localChannel){
        super(scheduledExecutorService, localChannel);

        this.resourceUri = resourceUri;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.info("Received response from service {}.", resourceUri);

        if(scheduledFuture != null){
            if(scheduledFuture.cancel(false))
                log.debug("Received update notification for {} within max-age.", resourceUri);
            else
                log.error("Received update notification for {} within max-age but something went wrong...",
                        resourceUri);
        }

        if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){

            log.warn("Observation of {} failed. CoAP response was: {}", resourceUri, coapResponse);
            return;
        }

        //create resource status message to update the cache
        ResourceStatusMessage resourceStatusMessage;
        try {
            resourceStatusMessage = ResourceStatusMessage.create(coapResponse, resourceUri);
            updateResourceStatus(resourceStatusMessage);
        } catch (Exception e) {
            log.error("Exception while creating resource status message from CoAP update notification.", e);
            return;
        }

        //log a warning about resource expiry, i.e. no update within max-age
        long maxAge = coapResponse.getMaxAge();
        scheduledFuture = scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                log.warn("No update notification from {} within max-age of last state.", resourceUri);
                //removeResource(new InternalRemoveResourceMessage(resourceUri));
            }
        }, maxAge, TimeUnit.SECONDS);
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                removeResource(new InternalRemoveResourceMessage(resourceUri));
            }
        }, 0, TimeUnit.SECONDS) ;
    }
}
