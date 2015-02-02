package eu.spitfire.ssp.backends.external.coap.registry;

import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import eu.spitfire.ssp.backends.external.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.external.coap.CoapWebservice;
import eu.spitfire.ssp.backends.generic.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * The {@link eu.spitfire.ssp.backends.external.coap.registry.CoapRegistry} starts a
 * {@link eu.spitfire.ssp.backends.external.coap.registry.CoapRegistryWebservice} on the
 * {@link de.uniluebeck.itm.ncoap.application.server.CoapServerApplication} returned by
 * {@link eu.spitfire.ssp.backends.external.coap.CoapBackendComponentFactory#getCoapServer()} and
 * waits for external CoAP Web Services to register.
 *
 * @author Oliver Kleine
 */
public class CoapRegistry extends Registry<URI, CoapWebservice> {

    private Logger log = LoggerFactory.getLogger(CoapRegistry.class.getName());

    private CoapBackendComponentFactory componentFactory;
    private CoapServerApplication coapServer;

    public CoapRegistry(CoapBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.componentFactory = componentFactory;
        this.coapServer = componentFactory.getCoapServer();
    }

    /**
     * Create the CoAP registry Web Service and start it.
     *
     * @throws Exception if some unexpected error occurred.
     */
    @Override
    public void startRegistry() throws Exception {
        coapServer.registerService(new CoapRegistryWebservice(componentFactory));
    }
}
