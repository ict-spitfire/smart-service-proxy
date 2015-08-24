package eu.spitfire.ssp.backend.coap;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.Token;
import de.uniluebeck.itm.ncoap.communication.observe.client.UpdateNotificationProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;
import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Date;

/**
 * The {@link CoapObserver} is the component to observe registered
 * CoAP Web Services (as {@link eu.spitfire.ssp.backend.generic.DataOrigin}) and update the SSPs cache according to
 * the observations, i.e. status changes.
 *
 * @author Oliver Kleine
 */
public class CoapObserver extends DataOriginObserver<URI, CoapWebservice> {

    private Logger log = LoggerFactory.getLogger(CoapObserver.class.getName());
    private CoapClientApplication coapClient;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.DataOriginObserver}.
     *
     * @param componentFactory the {@link eu.spitfire.ssp.backend.generic.ComponentFactory} that provides
     *                         all components to handle instances of
     *                         {@link CoapWebservice}.
     */
    protected CoapObserver(CoapComponentFactory componentFactory) {
        super(componentFactory);
        this.coapClient = componentFactory.getCoapClient();
    }

    /**
     * Starting an observation means to send a GET request with the
     * {@link de.uniluebeck.itm.ncoap.message.options.OptionValue.Name#OBSERVE} set to the CoAP Web Service to be
     * observed.
     *
     * @param coapWebservice the {@link CoapWebservice} to be observed
     */
    @Override
    public void startObservation(CoapWebservice coapWebservice) {
        try{
            URI webserviceUri = coapWebservice.getIdentifier();
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, webserviceUri);
            coapRequest.setAccept(ContentFormat.APP_RDF_XML);
            coapRequest.setAccept(ContentFormat.APP_N3);
            coapRequest.setAccept(ContentFormat.APP_TURTLE);
            coapRequest.setObserve();

            InetAddress remoteAddress = InetAddress.getByName(webserviceUri.getHost());
            int port = webserviceUri.getPort() == -1 ? 5683 : webserviceUri.getPort();

            coapClient.sendCoapRequest(coapRequest,
                    new CoapUpdateNotificationProcessor(webserviceUri), new InetSocketAddress(remoteAddress, port)
            );
        }
        catch(Exception ex){
            log.error("Could not start observation of {}!", coapWebservice.getIdentifier(), ex);
        }
    }


    private class CoapUpdateNotificationProcessor implements UpdateNotificationProcessor,
            RetransmissionTimeoutProcessor {


        private URI graphName;


        private CoapUpdateNotificationProcessor(URI graphName) {
            this.graphName = graphName;
        }


        @Override
        public void processRetransmissionTimeout(InetSocketAddress remoteEndpoint, int messageID, Token token) {
            log.error("Request to {} timed out!", graphName);
        }


        @Override
        public boolean continueObservation() {
            return true;
        }


        @Override
        public void processCoapResponse(CoapResponse coapResponse) {
            try{
                Model model = CoapTools.getModelFromCoapResponse(coapResponse);
                Date expiry = new Date(System.currentTimeMillis() + coapResponse.getMaxAge() * 1000);

                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, model, expiry);
                ListenableFuture<Void> cacheUpdateResult = updateCache(expiringNamedGraph);

                Futures.addCallback(cacheUpdateResult, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        log.debug("Successfully updated graph {}.", graphName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Error while updating graph {}", graphName);
                    }
                });

            }
            catch(Exception ex){
                log.error("Error while processing Update Notification from {}.", graphName, ex);
            }
        }
    }
}
