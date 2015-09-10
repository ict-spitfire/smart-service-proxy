package eu.spitfire.ssp.server.internal.message;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;

import java.net.URI;

/**
* Created by olli on 07.07.14.
*/
public class InternalCacheUpdateRequest {

    private final SettableFuture<Void> cacheUpdateFuture;

    private ExpiringNamedGraph expiringNamedGraph;
//    private SensorValueUpdate sensorValueUpdate;

    public InternalCacheUpdateRequest(ExpiringNamedGraph expiringNamedGraph) {
        this(expiringNamedGraph, null);
    }

//    public InternalCacheUpdateRequest(SensorValueUpdate sensorValueUpdate){
//        this(null, sensorValueUpdate);
//    }

    private InternalCacheUpdateRequest(ExpiringNamedGraph expiringNamedGraph, SensorValueUpdate sensorValueUpdate){
        this.expiringNamedGraph = expiringNamedGraph;
//        this.sensorValueUpdate = sensorValueUpdate;
        this.cacheUpdateFuture = SettableFuture.create();
    }

    public URI getGraphName(){
        return this.expiringNamedGraph.getGraphName();
    }

    public SettableFuture<Void> getCacheUpdateFuture(){
        return this.cacheUpdateFuture;
    }

    public ExpiringNamedGraph getExpiringNamedGraph() {
        return expiringNamedGraph;
    }

//    public SensorValueUpdate getSensorValueUpdate() {
//        return sensorValueUpdate;
//    }

    @Override
    public String toString(){
        return "[InternalCacheUpdateRequest] Graph: " + this.getGraphName();
    }
}
