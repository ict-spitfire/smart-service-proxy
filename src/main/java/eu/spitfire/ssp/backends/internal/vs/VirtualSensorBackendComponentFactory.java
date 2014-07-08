package eu.spitfire.ssp.backends.internal.vs;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorBackendComponentFactory extends BackendComponentFactory<URI, VirtualSensor> {

    private VirtualSensorAccessor virtualSensorAccessor;
    private VirtualSensorObserver virtualSensorObserver;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix the prefix of the backend in the given config (without the ".")
     * @param config the SSP config
     * @param localChannel the local channel
     * @param internalTasksExecutor the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                              e.g. translating and forwarding requests to data origins
     * @param ioExecutor the {@link java.util.concurrent.ExecutorService} for I/O tasks
     *
     * @throws java.lang.Exception if something went terribly wrong
     */
    public VirtualSensorBackendComponentFactory(String prefix, Configuration config, LocalServerChannel localChannel,
                                                ScheduledExecutorService internalTasksExecutor,
                                                ExecutorService ioExecutor) throws Exception {

        super(prefix, config, localChannel, internalTasksExecutor, ioExecutor);

        this.virtualSensorAccessor = new VirtualSensorAccessor(this);
        this.virtualSensorObserver = new VirtualSensorObserver(this);
    }


    @Override
    public void initialize() throws Exception {
        //Nothing to do...
    }


    @Override
    public VirtualSensorObserver getObserver(VirtualSensor virtualSensor) {
        return this.virtualSensorObserver;
    }


    @Override
    public VirtualSensorAccessor getAccessor(VirtualSensor virtualSensor) {
        return this.virtualSensorAccessor;
    }


    @Override
    public VirtualSensorRegistry createRegistry(Configuration config) throws Exception {
        return new VirtualSensorRegistry(this);
    }


    @Override
    public VirtualSensorRegistry getRegistry(){
        return (VirtualSensorRegistry) super.getRegistry();
    }

    @Override
    public void shutdown() {
        //Nothing to do, yet
    }
}
