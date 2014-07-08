package eu.spitfire.ssp.server.internal.messages;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Created by olli on 07.07.14.
 */
public class InternalCacheUpdateTask extends ExpiringNamedGraph{

    private final SettableFuture<Void> cacheUpdateFuture;

    public InternalCacheUpdateTask(ExpiringNamedGraph expiringNamedGraph) {
        super(expiringNamedGraph.getGraphName(), expiringNamedGraph.getGraph(), expiringNamedGraph.getExpiry());
        this.cacheUpdateFuture = SettableFuture.create();
    }

    public SettableFuture<Void> getCacheUpdateFuture(){
        return this.cacheUpdateFuture;
    }
}
