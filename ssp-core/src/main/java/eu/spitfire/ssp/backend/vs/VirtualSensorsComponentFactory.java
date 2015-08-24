package eu.spitfire.ssp.backend.vs;

import eu.spitfire.ssp.backend.generic.ComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorsComponentFactory extends ComponentFactory<URI, VirtualSensor> {

    private VirtualSensorsAccessor virtualSensorsAccessor;
    private VirtualSensorsObserver virtualSensorObserver;


    public VirtualSensorsComponentFactory(Configuration config, LocalServerChannel localChannel,
                                          ScheduledExecutorService internalTasksExecutor,
                                          ExecutorService ioExecutor) throws Exception {

        super("vs", config, localChannel, internalTasksExecutor, ioExecutor);

        this.virtualSensorsAccessor = new VirtualSensorsAccessor(this);
        this.virtualSensorObserver = new VirtualSensorsObserver(this);
    }


    @Override
    public void initialize() throws Exception {
        //Nothing to do...
    }


    @Override
    public VirtualSensorsObserver getObserver(VirtualSensor virtualSensor) {
        return this.virtualSensorObserver;
    }


    @Override
    public VirtualSensorsAccessor getAccessor(VirtualSensor virtualSensor) {
        return this.virtualSensorsAccessor;
    }


    @Override
    public VirtualSensorsRegistry createRegistry(Configuration config) throws Exception {
        return new VirtualSensorsRegistry(this);
    }


    @Override
    public VirtualSensorsRegistry getRegistry(){
        return (VirtualSensorsRegistry) super.getRegistry();
    }

    @Override
    public void shutdown() {
        //Nothing to do, yet
    }
}
