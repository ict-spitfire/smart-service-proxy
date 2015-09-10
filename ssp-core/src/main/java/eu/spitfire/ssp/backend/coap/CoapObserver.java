package eu.spitfire.ssp.backend.coap;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import com.google.common.util.concurrent.ListenableFuture;
import de.uzl.itm.ncoap.application.endpoint.CoapEndpoint;
import de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback;
import de.uzl.itm.ncoap.message.CoapRequest;
import de.uzl.itm.ncoap.message.CoapResponse;
import de.uzl.itm.ncoap.message.MessageCode;
import de.uzl.itm.ncoap.message.MessageType;
import de.uzl.itm.ncoap.message.options.ContentFormat;
import eu.spitfire.ssp.backend.coap.registry.CoapRegistry;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link CoapObserver} is the component to observe registered
 * CoAP Web Services (as {@link eu.spitfire.ssp.backend.generic.DataOrigin}) and update the SSPs cache according to
 * the observations, i.e. status changes.
 *
 * @author Oliver Kleine
 */
public class CoapObserver extends DataOriginObserver<URI, CoapWebresource> {

    private Logger log = LoggerFactory.getLogger(CoapObserver.class.getName());
    private CoapEndpoint coapApplication;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.DataOriginObserver}.
     *
     * @param componentFactory the {@link eu.spitfire.ssp.backend.generic.ComponentFactory} that provides
     *                         all components to handle instances of
     *                         {@link CoapWebresource}.
     */
    protected CoapObserver(CoapComponentFactory componentFactory) {
        super(componentFactory);
        this.coapApplication = componentFactory.getCoapApplication();
    }

    /**
     * Starting an observation means to send a GET request with the
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#OBSERVE} set to the CoAP Web Service to be
     * observed.
     *
     * @param coapWebresource the {@link CoapWebresource} to be observed
     */
    @Override
    public void startObservation(CoapWebresource coapWebresource) {
        try{
            URI webserviceUri = coapWebresource.getIdentifier();
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, webserviceUri);
            coapRequest.setAccept(ContentFormat.APP_RDF_XML);
            coapRequest.setAccept(ContentFormat.APP_N3);
            coapRequest.setAccept(ContentFormat.APP_TURTLE);
            coapRequest.setObserve(0);
            coapRequest.setEndpointID1();

            InetAddress remoteAddress = InetAddress.getByName(webserviceUri.getHost());
            int port = webserviceUri.getPort() == -1 ? 5683 : webserviceUri.getPort();

            coapApplication.sendCoapRequest(coapRequest,
                    new UpdateNotificationCallback(webserviceUri), new InetSocketAddress(remoteAddress, port)
            );
        }
        catch(Exception ex){
            log.error("Could not start observation of {}!", coapWebresource.getIdentifier(), ex);
        }
    }


    private class UpdateNotificationCallback extends ClientCallback {

        private URI graphName;
        private InetSocketAddress updatedRemoteSocket;
        private AtomicInteger retransmissions;

        private UpdateNotificationCallback(URI graphName) {
            this.graphName = graphName;
            this.updatedRemoteSocket = null;
            this.retransmissions = new AtomicInteger(0);
        }

        @Override
        public void processTransmissionTimeout() {
            log.error("Request to {} timed out!", graphName);
        }

        @Override
        public void processRetransmission(){
            log.warn("Retransmission #{} (Graph: {}", retransmissions.incrementAndGet(), graphName);
        }

        @Override
        public boolean continueObservation() {
            return true;
        }

        @Override
        public synchronized void processRemoteSocketChanged(InetSocketAddress remoteSocket, InetSocketAddress previous){
            this.updatedRemoteSocket = remoteSocket;
            log.info("Socket changed from \"{}\" to \"{}\" (Graph: {})", new Object[]{previous, remoteSocket, graphName});
        }

        @Override
        public synchronized void processCoapResponse(CoapResponse coapResponse) {
            log.info("Process Update notification for \"{}\".", graphName);
            try{
                 // the remote changed since the last update notification
                if(updatedRemoteSocket != null){
                    CoapRegistry registry = (CoapRegistry) getRegistry();

                    // create "old" data origin
                    CoapWebresource oldDataOrigin = new CoapWebresource(this.graphName);

                    // create "new" data origin
                    String path = this.graphName.getPath();
                    String host = updatedRemoteSocket.getAddress().getHostAddress();
                    int port = updatedRemoteSocket.getPort();
                    final URI graphName = new URI("coap", null, host, port, path, null, null);
                    CoapWebresource newDataOrigin = new CoapWebresource(graphName);

                    // start replacement
                    ListenableFuture<Void> replacementFuture = registry.replaceDataOrigin(oldDataOrigin, newDataOrigin);
                    Futures.addCallback(replacementFuture, new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            log.debug("Successfully registered new data origin (new authority) {}.", graphName);
                            UpdateNotificationCallback.this.graphName = graphName;
                            UpdateNotificationCallback.this.updatedRemoteSocket = null;
                            updateCache(coapResponse);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.error("Could not register new data origin (new authority) {}.", graphName);
                            UpdateNotificationCallback.this.updatedRemoteSocket = null;
                        }
                    });
                }

                // the remote socket did not change since the last update notification
                else{
                    updateCache(coapResponse);
                }
            }
            catch(Exception ex){
                log.error("Error while processing Update Notification from {}.", graphName, ex);
            }
            finally {
                this.retransmissions.set(0);
            }
        }

        private void updateCache(CoapResponse coapResponse){

            Model model = CoapTools.getModelFromCoapResponse(coapResponse);

            // regular update notifications
            if(model != null) {
                Date expiry = new Date(System.currentTimeMillis() + coapResponse.getMaxAge() * 1000);
                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, model, expiry);

                Futures.addCallback(CoapObserver.super.updateCache(expiringNamedGraph), new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        log.debug("Successfully updated graph {}.", graphName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Error while updating graph {}", graphName, t);
                    }
                });
            }

            // handle error response (e.g. because of shutdown of remote server)
            else{
                Futures.addCallback(getRegistry().unregisterDataOrigin(graphName), new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        log.debug("DELETED named graph because of error response {}.", graphName);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        log.error("Error while deleting graph {} because of error response!", graphName, throwable);
                    }
                });
            }
        }
    }
}
