package eu.spitfire.ssp.backend.coap.registry;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uzl.itm.ncoap.application.endpoint.CoapEndpoint;
import de.uzl.itm.ncoap.application.server.webresource.NotObservableWebresource;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.LinkAttribute;
import de.uzl.itm.ncoap.message.CoapRequest;
import de.uzl.itm.ncoap.message.CoapResponse;
import de.uzl.itm.ncoap.message.MessageCode;
import de.uzl.itm.ncoap.message.MessageType;
import de.uzl.itm.ncoap.message.options.OptionValue;
import eu.spitfire.ssp.backend.coap.CoapWebresource;
import eu.spitfire.ssp.backend.coap.CoapComponentFactory;
import eu.spitfire.ssp.backend.generic.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapRegistry} starts a
 * {@link CoapRegistryWebservice} on the
 * {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint} returned by
 * {@link eu.spitfire.ssp.backend.coap.CoapComponentFactory#getCoapApplication()} and
 * waits for external CoAP Web Services to register.
 *
 * @author Oliver Kleine
 */
public class CoapRegistry extends Registry<URI, CoapWebresource> {

    private int nextGraphNo;

    private static Logger LOG = LoggerFactory.getLogger(CoapRegistry.class.getName());

    private CoapComponentFactory componentFactory;
    private CoapEndpoint coapApplication;

    public CoapRegistry(CoapComponentFactory componentFactory) {
        super(componentFactory);
        this.nextGraphNo = 1;
        this.componentFactory = componentFactory;
        this.coapApplication = componentFactory.getCoapApplication();
    }


    /**
     * Create the CoAP registry Web Service and start it.
     *
     * @throws Exception if some unexpected error occurred.
     */
    @Override
    public void startRegistry() throws Exception {
        coapApplication.registerWebresource(new CoapRegistryWebservice(componentFactory));
    }


    private class CoapRegistryWebservice extends NotObservableWebresource<Void> {

        private Logger log = LoggerFactory.getLogger(CoapRegistryWebservice.class.getName());

        private CoapEndpoint coapApplication;

        /**
         * Creates a new instance of {@link CoapRegistryWebservice}.
         *
         * @param componentFactory the {@link eu.spitfire.ssp.backend.coap.CoapComponentFactory} to
         *                         get the {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint}, and the
         *                         {@link CoapRegistry} from.
         */
        public CoapRegistryWebservice(CoapComponentFactory componentFactory) {
            super("/registry", null, OptionValue.MAX_AGE_DEFAULT, componentFactory.getInternalTasksExecutor());
            this.coapApplication = componentFactory.getCoapApplication();
        }


        @Override
        public void processCoapRequest(final SettableFuture<CoapResponse> registrationResponseFuture,
                                       final CoapRequest coapRequest, InetSocketAddress remoteAddress) {

            try {
                log.info("Received CoAP registration message from {}: {}", remoteAddress.getAddress(), coapRequest);

                //Only POST message are allowed
                if (coapRequest.getMessageCodeName() != MessageCode.Name.POST) {
                    CoapResponse coapResponse = CoapResponse.createErrorResponse(coapRequest.getMessageTypeName(),
                            MessageCode.Name.METHOD_NOT_ALLOWED_405, "Only POST messages are allowed!");

                    registrationResponseFuture.set(coapResponse);
                    return;
                }


                // Get, await and handle the set of available Services on the newly registered Server
                Futures.addCallback(requestWellKnownCore(remoteAddress), new FutureCallback<Map<String, Set<LinkAttribute>>>() {
                    @Override
                    public void onSuccess(Map<String, Set<LinkAttribute>> resources) {
                        try {
                            HashSet<ListenableFuture<Void>> registrationFutures = new HashSet<>();

                            for (String uriPath : resources.keySet()) {
                                String remoteHost = remoteAddress.getAddress().getHostAddress();
                                int remotePort = remoteAddress.getPort() == 5683 ? -1 : remoteAddress.getPort();
                                URI resourceUri = new URI("coap", null, remoteHost, remotePort, uriPath, null, null);
                                log.debug("New CoAP resource: {}", resourceUri);

                                // register new data origin
                                CoapWebresource dataOrigin = new CoapWebresource(resourceUri);
                                registrationFutures.add(registerDataOrigin(dataOrigin));
                            }

                            ListenableFuture<List<Void>> combinedRegistrationFuture = Futures.successfulAsList(registrationFutures);

                            Futures.addCallback(combinedRegistrationFuture, new FutureCallback<List<Void>>() {
                                @Override
                                public void onSuccess(List<Void> voids) {
                                    CoapResponse coapResponse = new CoapResponse(MessageType.Name.NON, MessageCode.Name.CREATED_201);
                                    registrationResponseFuture.set(coapResponse);
                                }

                                @Override
                                public void onFailure(Throwable throwable) {
                                    log.error("This should never happen!", throwable);
                                    registrationResponseFuture.setException(throwable);
                                }
                            });

                        } catch (Exception ex) {
                            log.error("Error while creating URI!", ex);
                            registrationResponseFuture.setException(ex);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        registrationResponseFuture.setException(t);
                    }
                });
            } catch (Exception ex) {
                registrationResponseFuture.setException(ex);
            }
        }

        /**
         * Returns an empty byte array as there is only POST allowed and no content provided. However, this method is only
         * implemented for the sake of completeness and is not used at all by the framework.
         *
         * @param contentFormat the number representing the desired content format
         * @return an empty byte array
         */
        @Override
        public byte[] getSerializedResourceStatus(long contentFormat) {
            return new byte[0];
        }


        private ListenableFuture<Map<String, Set<LinkAttribute>>> requestWellKnownCore(final InetSocketAddress remoteSocket) throws Exception {

            // create URI
            final String remoteHost = remoteSocket.getHostName();
            final int remotePort = remoteSocket.getPort() == 5683 ? -1 : remoteSocket.getPort();
            URI uri = new URI("coap", null, remoteHost, remotePort, "/.well-known/core", null, null);

            // create and send request
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, uri);
            WellKnownCoreCallback wncCallback = new WellKnownCoreCallback();
            this.coapApplication.sendCoapRequest(coapRequest, wncCallback, remoteSocket);

            // return the future
            return wncCallback.getWellKnownCoreFuture();
        }

        @Override
        public byte[] getEtag(long contentFormat) {
            return new byte[1];
        }

        @Override
        public void updateEtag(Void resourceStatus) {
            // nothing to do
        }

        @Override
        public void shutdown() {
            // nothing to do
        }
    }
}
