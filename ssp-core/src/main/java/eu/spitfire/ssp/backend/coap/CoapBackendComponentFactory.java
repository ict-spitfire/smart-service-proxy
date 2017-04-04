package eu.spitfire.ssp.backend.coap;


import de.uzl.itm.ncoap.application.endpoint.CoapEndpoint;
import de.uzl.itm.ncoap.communication.dispatching.server.NotFoundHandler;
import eu.spitfire.ssp.backend.coap.registry.CoapWebresourceRegistry;
import eu.spitfire.ssp.backend.generic.BackendComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapBackendComponentFactory} provides all components that are
 * either mandatory, i.e. due to inheritance from  {@link eu.spitfire.ssp.backend.generic.BackendComponentFactory} or
 * shared by multiple components, i.e. the {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint}.
 *
 * @author Oliver Kleine
 */
public class CoapBackendComponentFactory extends BackendComponentFactory<URI, CoapWebresource> {

    private static Logger LOG = LoggerFactory.getLogger(CoapBackendComponentFactory.class.getName());

    private CoapEndpoint coapApplication;
    private CoapWebresourceRegistry registry;
    private CoapWebresourceAccessor accessor;
    private CoapWebresourceObserver observer;


    public CoapBackendComponentFactory(Configuration config, LocalServerChannel localChannel,
                                       ScheduledExecutorService internalTasksExecutor, ExecutorService ioExecutor)
            throws Exception {

        super("coap", config, localChannel, internalTasksExecutor, ioExecutor);

        InetSocketAddress socketAddress = new InetSocketAddress(5683);
        this.coapApplication = new CoapEndpoint("SSP CoAP", NotFoundHandler.getDefault(), socketAddress);
        this.registry = new CoapWebresourceRegistry(this);
        this.accessor = new CoapWebresourceAccessor(this);
        this.observer = new CoapWebresourceObserver(this);
    }


    @Override
    public void initialize() throws Exception {
        //Nothing to do...
    }

    /**
     * Returns the {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint} to communicate with
     * external CoAP servers and clients.
     *
     * @return the {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint} to communicate with
     * external CoAP servers and clients.
     */
    public CoapEndpoint getCoapApplication(){
        return this.coapApplication;
    }


    @Override
    public CoapWebresourceObserver getObserver(CoapWebresource externalWebservice) {
        return this.observer;
    }

    @Override
    public CoapWebresourceAccessor getAccessor(CoapWebresource externalWebservice) {
        return this.accessor;
    }

    @Override
    public CoapWebresourceRegistry createRegistry(Configuration config) throws Exception {
        return this.registry;
    }

    @Override
    public CoapWebresourceRegistry getRegistry() {
        return this.registry;
    }

    @Override
    public void shutdown() {
        this.coapApplication.shutdown();
    }
}
