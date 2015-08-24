package eu.spitfire.ssp.backend.coap;

import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import eu.spitfire.ssp.backend.coap.registry.CoapRegistry;
import eu.spitfire.ssp.backend.generic.ComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapComponentFactory} provides all components that are
 * either mandatory, i.e. due to inheritance from  {@link eu.spitfire.ssp.backend.generic.ComponentFactory} or
 * shared by multiple components, i.e. the {@link de.uniluebeck.itm.ncoap.application.client.CoapClientApplication}
 * and the {@link de.uniluebeck.itm.ncoap.application.server.CoapServerApplication}.
 *
 * @author Oliver Kleine
 */
public class CoapComponentFactory extends ComponentFactory<URI, CoapWebservice> {

    private CoapClientApplication coapClient;
    private CoapServerApplication coapServer;
    private CoapRegistry registry;
    private CoapAccessor accessor;
    private CoapObserver observer;


    public CoapComponentFactory(Configuration config, LocalServerChannel localChannel,
                                ScheduledExecutorService internalTasksExecutor, ExecutorService ioExecutor)
            throws Exception {

        super("coap", config, localChannel, internalTasksExecutor, ioExecutor);

        this.coapClient = new CoapClientApplication("SSP CoAP Client");
        this.coapServer = new CoapServerApplication();

        this.registry = new CoapRegistry(this);
        this.accessor = new CoapAccessor(this);
        this.observer = new CoapObserver(this);
    }


    @Override
    public void initialize() throws Exception {
        //Nothing to do...
    }

    /**
     * Returns the {@link de.uniluebeck.itm.ncoap.application.client.CoapClientApplication} to communicate with
     * external CoAP servers.
     *
     * @return the {@link de.uniluebeck.itm.ncoap.application.client.CoapClientApplication} to communicate with
     * external CoAP servers.
     */
    public CoapClientApplication getCoapClient(){
        return this.coapClient;
    }

    /**
     * Returns the {@link de.uniluebeck.itm.ncoap.application.server.CoapServerApplication} to provide services such as
     * the registry.
     *
     * @return the {@link de.uniluebeck.itm.ncoap.application.server.CoapServerApplication} to provide services such as
     * the registry.
     */
    public CoapServerApplication getCoapServer(){
        return this.coapServer;
    }


    @Override
    public CoapObserver getObserver(CoapWebservice externalWebservice) {
        return this.observer;
    }

    @Override
    public CoapAccessor getAccessor(CoapWebservice externalWebservice) {
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
        this.coapServer.shutdown();
    }
}
