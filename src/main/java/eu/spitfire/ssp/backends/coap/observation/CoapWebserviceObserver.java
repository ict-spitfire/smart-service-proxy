//package eu.spitfire.ssp.backends.coap.observation;
//
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.rdf.model.*;
//import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
//import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
//import de.uniluebeck.itm.ncoap.communication.observe.ObservationTimeoutProcessor;
//import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
//import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
//import de.uniluebeck.itm.ncoap.message.CoapRequest;
//import de.uniluebeck.itm.ncoap.message.CoapResponse;
//import de.uniluebeck.itm.ncoap.message.header.Code;
//import de.uniluebeck.itm.ncoap.message.header.MsgType;
//import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
//import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
//import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
//import eu.spitfire.ssp.backends.coap.CoapWebserviceResponseProcessor;
//import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
//import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
//import org.jboss.netty.channel.Channels;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.util.Date;
//import java.util.concurrent.ExecutorService;
//
//
///**
// * Instances of {@link CoapWebserviceObserver} are instances of {@link CoapResponseProcessor} to observe
// * CoAP webservices. If an update notification was received for an observed CoAP resource it sends
// * an {@link eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage} to update the actual resource status
// * in the cache.
// *
// * @author Oliver Kleine
// */
//public class CoapWebserviceObserver extends CoapWebserviceResponseProcessor implements CoapResponseProcessor,
//        RetransmissionTimeoutProcessor {
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//    private CoapClientApplication coapClientApplication;
//    private URI dataOrigin;
//    private ExecutorService executorService;
//
//
//    public CoapWebserviceObserver(CoapBackendComponentFactory backendComponentFactory, URI dataOrigin) {
//        super(backendComponentFactory);
//        this.dataOrigin = dataOrigin;
//        this.coapClientApplication = backendComponentFactory.getCoapClientApplication();
//        this.executorService = backendComponentFactory.getExecutorService();
//    }
//
//
//    public void startObservation() {
//        try {
//            log.info("Start observation of service {}", dataOrigin);
//            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, dataOrigin);
//            coapRequest.setObserveOptionRequest();
//            coapRequest.setAccept(OptionRegistry.MediaType.APP_SHDT, OptionRegistry.MediaType.APP_RDF_XML,
//                    OptionRegistry.MediaType.APP_N3, OptionRegistry.MediaType.APP_TURTLE);
//
//            this.coapClientApplication.writeCoapRequest(coapRequest, this);
//
//        } catch (Exception e) {
//            log.error("This should never happen! Could not start observation of data origin {}.", dataOrigin, e);
//        }
//    }
//
//    @Override
//    public void processCoapResponse(final CoapResponse coapResponse) {
//        executorService.submit(new Runnable() {
//            @Override
//            public void run() {
//                log.info("Received update notification from service {}: {}", dataOrigin, coapResponse);
//
//                if (coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
//                        || coapResponse.getCode().isErrorMessage()) {
//                    log.warn("Observation of {} failed. CoAP response was: {}", dataOrigin, coapResponse);
//                    return;
//                }
//
//                //create resource status message to update the cache
//                try {
//                    Model model = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
//                    final Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);
//
//                    if (dataOrigin.getPath().endsWith("_minimal"))
//                        updateResourceStatus(model.listStatements().nextStatement(), expiry);
//                    else
//                        cacheResourcesStates(model, expiry);
//                } catch (Exception e) {
//                    log.error("Exception while creating resource status message from CoAP update notification.", e);
//                    restartObservation();
//                }
//            }
//        });
//    }
//
//    private void restartObservation() {
//        this.startObservation();
//    }
//
//    @Override
//    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
//        try {
//            log.warn("Request for service {} timed out. Stop observation.", dataOrigin);
//            URI resourceUri;
//            if (dataOrigin.getPath().endsWith("location/_minimal")) {
//                resourceUri = new URI(dataOrigin.getScheme(), null, dataOrigin.getHost(), -1, "/rdf", null, null);
//            } else if (dataOrigin.getPath().endsWith("_minimal")) {
//                String resourcePath = dataOrigin.getPath().substring(0, dataOrigin.getPath().indexOf("/_minimal"));
//                resourceUri = new URI(dataOrigin.getScheme(), null, dataOrigin.getHost(), -1, resourcePath, null, null);
//            } else {
//                resourceUri = dataOrigin;
//            }
//
//            deleteResource(resourceUri);
//        } catch (Exception e) {
//            log.error("This should never happen.", e);
//        }
//    }
//
//
//    @Override
//    public void processObservationTimeout(InetSocketAddress remoteAddress) {
//        log.warn("Observation for service {} timed out. Try to restart observation.", dataOrigin);
//        restartObservation();
//    }
//}
