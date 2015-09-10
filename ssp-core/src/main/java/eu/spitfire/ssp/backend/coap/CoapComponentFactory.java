package eu.spitfire.ssp.backend.coap;


import de.uzl.itm.ncoap.application.endpoint.CoapEndpoint;
import de.uzl.itm.ncoap.communication.dispatching.server.NotFoundHandler;
import eu.spitfire.ssp.backend.coap.registry.CoapRegistry;
import eu.spitfire.ssp.backend.generic.ComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapComponentFactory} provides all components that are
 * either mandatory, i.e. due to inheritance from  {@link eu.spitfire.ssp.backend.generic.ComponentFactory} or
 * shared by multiple components, i.e. the {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint}.
 *
 * @author Oliver Kleine
 */
public class CoapComponentFactory extends ComponentFactory<URI, CoapWebresource> {

    private static Logger LOG = LoggerFactory.getLogger(CoapComponentFactory.class.getName());

    private CoapEndpoint coapApplication;
    private CoapRegistry registry;
    private CoapAccessor accessor;
    private CoapObserver observer;


    public CoapComponentFactory(Configuration config, LocalServerChannel localChannel,
                                ScheduledExecutorService internalTasksExecutor, ExecutorService ioExecutor)
            throws Exception {

        super("coap", config, localChannel, internalTasksExecutor, ioExecutor);

        InetSocketAddress socketAddress = new InetSocketAddress(5683);
        this.coapApplication = new CoapEndpoint("SSP CoAP", NotFoundHandler.getDefault(), socketAddress);
        this.registry = new CoapRegistry(this);
        this.accessor = new CoapAccessor(this);
        this.observer = new CoapObserver(this);
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
    public CoapObserver getObserver(CoapWebresource externalWebservice) {
        return this.observer;
    }

    @Override
    public CoapAccessor getAccessor(CoapWebresource externalWebservice) {
        return this.accessor;
    }

    @Override
    public CoapRegistry createRegistry(Configuration config) throws Exception {
        return this.registry;
    }

    @Override
    public CoapRegistry getRegistry() {
        return this.registry;
    }

    @Override
    public void shutdown() {
        this.coapApplication.shutdown();
    }
}
