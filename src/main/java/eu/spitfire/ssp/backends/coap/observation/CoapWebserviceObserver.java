package eu.spitfire.ssp.backends.coap.observation;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.observe.ObservationTimeoutProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.backends.coap.requestprocessing.CoapWebserviceResponseProcessor;
import eu.spitfire.ssp.backends.utils.BackendManager;
import eu.spitfire.ssp.backends.utils.DataOriginObserver;
import eu.spitfire.ssp.backends.utils.ResourceToolbox;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Instances of {@link CoapWebserviceObserver} are instances of {@link CoapResponseProcessor} to observe
 * CoAP webservices. If an update notification was received for an observed CoAP resource it sends
 * an {@link eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage} to update the actual resource status in the cache.
 *
 * @author Oliver Kleine
 */
public class CoapWebserviceObserver extends DataOriginObserver<URI> implements CoapResponseProcessor,
        RetransmissionTimeoutProcessor, ObservationTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    private CoapRequest coapRequest;


    /**
     * @param coapRequest the {@link CoapRequest} to be sent if the observation times out (max-age expiry)
     */
    public CoapWebserviceObserver(BackendManager<URI> backendManager, CoapRequest coapRequest){
        super(backendManager);
        this.coapRequest = coapRequest;
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
            Model resourceStatus = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
            final Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);

            final Map<URI, Model> resources = ResourceToolbox.getModelsPerSubject(resourceStatus);

            for(final URI subResourceUri : resources.keySet()){
                backendManager.getScheduledExecutorService().schedule(new Runnable(){

                    @Override
                    public void run() {
                        cacheResourceStatus(subResourceUri, resources.get(subResourceUri), expiry);
                    }
                }, 0, TimeUnit.MILLISECONDS);

            }

        } catch (Exception e) {
            log.error("Exception while creating resource status message from CoAP update notification.", e);
            return;
        }
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        backendManager.getScheduledExecutorService().schedule(new Runnable(){
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
