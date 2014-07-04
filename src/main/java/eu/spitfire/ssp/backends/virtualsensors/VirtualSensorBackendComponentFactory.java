package eu.spitfire.ssp.backends.virtualsensors;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorBackendComponentFactory extends BackendComponentFactory<URI> {

    private VirtualSensorAccessor virtualSensorAccessor;
    private VirtualSensorRegistry virtualSensorRegistry;
    private VirtualSensorObserver virtualSensorObserver;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix                       the prefix of the backend in the given config (without the ".")
     * @param config                       the SSP config
     * @param localChannel
     * @param internalTasksExecutor the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                     e.g. translating and forwarding requests to data origins
     * @param ioExecutor            @throws java.lang.Exception if something went terribly wrong
     */
    public VirtualSensorBackendComponentFactory(String prefix, Configuration config, LocalServerChannel localChannel,
                                                ScheduledExecutorService internalTasksExecutor,
                                                ExecutorService ioExecutor) throws Exception {

        super(prefix, config, localChannel, internalTasksExecutor, ioExecutor);

        this.virtualSensorAccessor = new VirtualSensorAccessor(this);
        this.virtualSensorRegistry = new VirtualSensorRegistry(this);
        this.virtualSensorObserver = new VirtualSensorObserver(this);
    }


    @Override
    public void initialize() throws Exception {
        //Nothing to do...
    }

    @Override
    public VirtualSensorObserver getObserver(DataOrigin<URI> dataOrigin) {
        return this.virtualSensorObserver;
    }

    @Override
    public VirtualSensorAccessor getAccessor(DataOrigin<URI> dataOrigin) {
        return this.virtualSensorAccessor;
    }

    @Override
    public VirtualSensorRegistry createRegistry(Configuration config) throws Exception {
        return this.virtualSensorRegistry;
    }

    @Override
    public VirtualSensorRegistry getRegistry(){
        return this.virtualSensorRegistry;
    }

    @Override
    public void shutdown() {
        //Nothing to do, yet
    }
}
