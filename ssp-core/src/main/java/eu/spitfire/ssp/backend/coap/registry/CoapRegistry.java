package eu.spitfire.ssp.backend.coap.registry;

import de.uzl.itm.ncoap.application.peer.CoapPeerApplication;
import eu.spitfire.ssp.backend.coap.CoapWebservice;
import eu.spitfire.ssp.backend.coap.CoapComponentFactory;
import eu.spitfire.ssp.backend.generic.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * The {@link CoapRegistry} starts a
 * {@link CoapRegistryWebservice} on the
 * {@link de.uzl.itm.ncoap.application.peer.CoapPeerApplication} returned by
 * {@link eu.spitfire.ssp.backend.coap.CoapComponentFactory#getCoapApplication()} and
 * waits for external CoAP Web Services to register.
 *
 * @author Oliver Kleine
 */
public class CoapRegistry extends Registry<URI, CoapWebservice> {

    private static Logger LOG = LoggerFactory.getLogger(CoapRegistry.class.getName());

    private CoapComponentFactory componentFactory;
    private CoapPeerApplication coapApplication;

    public CoapRegistry(CoapComponentFactory componentFactory) {
        super(componentFactory);
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
        coapApplication.registerResource(new CoapRegistryWebservice(componentFactory));
    }
}
