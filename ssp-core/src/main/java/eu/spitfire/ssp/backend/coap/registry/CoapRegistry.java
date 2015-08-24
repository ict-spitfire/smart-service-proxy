package eu.spitfire.ssp.backend.coap.registry;

import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import eu.spitfire.ssp.backend.coap.CoapWebservice;
import eu.spitfire.ssp.backend.coap.CoapComponentFactory;
import eu.spitfire.ssp.backend.generic.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * The {@link CoapRegistry} starts a
 * {@link CoapRegistryWebservice} on the
 * {@link de.uniluebeck.itm.ncoap.application.server.CoapServerApplication} returned by
 * {@link eu.spitfire.ssp.backend.coap.CoapComponentFactory#getCoapServer()} and
 * waits for external CoAP Web Services to register.
 *
 * @author Oliver Kleine
 */
public class CoapRegistry extends Registry<URI, CoapWebservice> {

    private Logger log = LoggerFactory.getLogger(CoapRegistry.class.getName());

    private CoapComponentFactory componentFactory;
    private CoapServerApplication coapServer;

    public CoapRegistry(CoapComponentFactory componentFactory) {
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
