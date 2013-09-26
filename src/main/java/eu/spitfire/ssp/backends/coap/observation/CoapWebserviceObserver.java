package eu.spitfire.ssp.backends.coap.observation;

import com.hp.hpl.jena.rdf.model.*;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.observe.ObservationTimeoutProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.InvalidMessageException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import de.uniluebeck.itm.ncoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import de.uniluebeck.itm.ncoap.message.options.ToManyOptionsException;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.backends.BackendComponentFactory;
import eu.spitfire.ssp.backends.DataOriginObserver;
import eu.spitfire.ssp.backends.ResourceToolbox;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * Instances of {@link CoapWebserviceObserver} are instances of {@link CoapResponseProcessor} to observe
 * CoAP webservices. If an update notification was received for an observed CoAP resource it sends
 * an {@link eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage} to update the actual resource status
 * in the cache.
 *
 * @author Oliver Kleine
 */
public class CoapWebserviceObserver extends DataOriginObserver<URI> implements CoapResponseProcessor,
        RetransmissionTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    public CoapWebserviceObserver(BackendComponentFactory<URI> backendComponentFactory, URI serviceUri){
        super(backendComponentFactory, serviceUri);
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.info("Received update notification from service {}.", this.getDataOrigin());

        if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){
            log.warn("Observation of {} failed. CoAP response was: {}", this.getDataOrigin(), coapResponse);
            return;
        }

        //create resource status message to update the cache
        try {
            Model model = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
            final Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);

            //final Map<URI, Model> resources = ResourceToolbox.getModelsPerSubject(model);

            ResIterator iterator = model.listSubjects();
            while(iterator.hasNext()){
                final Resource resource = iterator.nextResource();

                backendComponentFactory.getScheduledExecutorService().schedule(new Runnable(){
                    @Override
                    public void run() {
                        //Create new model per resource
                        Model resourceModel = ModelFactory.createDefaultModel();
                        StmtIterator stmtIterator = resource.listProperties();

                        while(stmtIterator.hasNext()){
                            Statement statement = stmtIterator.nextStatement();
                            resourceModel.add(statement);
                        }

                        Date expiry2 = new Date(System.currentTimeMillis() + 10000);
                        //Send resource with new status to cache
                        cacheResourceStatus(resource, expiry2);
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
        log.warn("Request for service {} timed out.", this.getDataOrigin());
        removeAllResources(getDataOrigin());
    }

    @Override
    public void handleObservationTimeout(URI serviceUri){
        log.warn("Observation for service {} timed out. Try to restart observation.", serviceUri);
        removeAllResources(getDataOrigin());
//        try {
//            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
//            coapRequest.setObserveOptionRequest();
//
//            ((CoapBackendComponentFactory) backendComponentFactory).getCoapClientApplication()
//                    .writeCoapRequest(coapRequest, this);
//        }
//        catch (Exception e) {
//            log.error("Could not try to restart observation of service {}", serviceUri , e);
//        }
    }
}
