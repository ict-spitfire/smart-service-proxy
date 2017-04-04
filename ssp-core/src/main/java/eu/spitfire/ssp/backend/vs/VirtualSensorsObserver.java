package eu.spitfire.ssp.backend.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backend.generic.BackendComponentFactory;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Oliver Kleine
 */
public class VirtualSensorsObserver extends DataOriginObserver<URI, VirtualSensor> implements Observer {

    private static Logger LOG = LoggerFactory.getLogger(VirtualSensorsObserver.class.getName());

    protected VirtualSensorsObserver(BackendComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
    }

    /**
     * Registers this instance of {@link VirtualSensorsObserver} at the
     * {@link VirtualSensor} and calls
     * {@link VirtualSensor#startPeriodicObservations(int, java.util.concurrent.TimeUnit)}
     *
     * <b>Note:</b> The {@link java.util.Observable} is (for internal reasons) not the
     * {@link VirtualSensor} but its
     * {@link VirtualSensor.ObservationValue}
     *
     * @param virtualSensor the {@link VirtualSensor } whose
     * {@link VirtualSensor.ObservationValue} is to be observed
     */
    @Override
    public void startObservation(final VirtualSensor virtualSensor) {
        virtualSensor.addObserver(this);
        virtualSensor.startPeriodicObservations(60, TimeUnit.SECONDS);
    }

    /**
     * This method is called by instances of {@link VirtualSensor.ObservationValue}.
     *
     * @param observable an instance of {@link VirtualSensor.ObservationValue}
     *
     * @param object the instance of {@link VirtualSensor} which provides the
     * {@link VirtualSensor.ObservationValue} that called this method
     */
    @Override
    public void update(Observable observable, Object object) {
        if(!(object instanceof VirtualSensor)){
            LOG.error("This should never happen (Object was no instance of VirtualSensor)!");
            return;
        }

        VirtualSensor virtualSensor = (VirtualSensor) object;

        // create new expiring named graph
        ExpiringNamedGraph graph = new ExpiringNamedGraph(
            virtualSensor.getGraphName(), virtualSensor.createGraphAsModel()
        );

        // update the cache
        ListenableFuture<Void> updateFuture = updateCache(graph);
        Futures.addCallback(updateFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                LOG.info("Successfully updated virtual sensor: {}", virtualSensor.getGraphName());
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Failed to update virtual sensor: {}", virtualSensor.getGraphName());
            }
        });
    }
}
