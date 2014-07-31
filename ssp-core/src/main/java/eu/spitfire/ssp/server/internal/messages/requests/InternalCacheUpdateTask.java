package eu.spitfire.ssp.server.internal.messages.requests;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;

import java.net.URI;

/**
 * Created by olli on 07.07.14.
 */
public class InternalCacheUpdateTask {

    private final SettableFuture<Void> cacheUpdateFuture;

    private ExpiringNamedGraph expiringNamedGraph;
    private SensorValueUpdate sensorValueUpdate;

    public InternalCacheUpdateTask(ExpiringNamedGraph expiringNamedGraph) {
        this(expiringNamedGraph, null);
    }

    public InternalCacheUpdateTask(SensorValueUpdate sensorValueUpdate){
        this(null, sensorValueUpdate);
    }

    private InternalCacheUpdateTask(ExpiringNamedGraph expiringNamedGraph, SensorValueUpdate sensorValueUpdate){
        this.expiringNamedGraph = expiringNamedGraph;
        this.sensorValueUpdate = sensorValueUpdate;
        this.cacheUpdateFuture = SettableFuture.create();
    }

    public URI getGraphName(){
        if(!(this.expiringNamedGraph == null)){
            return this.expiringNamedGraph.getGraphName();
        }
        else{
            return this.sensorValueUpdate.getSensorGraphName();
        }
    }

    public SettableFuture<Void> getCacheUpdateFuture(){
        return this.cacheUpdateFuture;
    }

    public ExpiringNamedGraph getExpiringNamedGraph() {
        return expiringNamedGraph;
    }

    public SensorValueUpdate getSensorValueUpdate() {
        return sensorValueUpdate;
    }
}
