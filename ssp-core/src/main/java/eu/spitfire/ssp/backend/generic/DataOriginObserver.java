package eu.spitfire.ssp.backend.generic;

import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.message.InternalCacheUpdateRequest;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link DataOriginObserver} is the component to observe the status of one or more instances of
 * {@link eu.spitfire.ssp.backend.generic.DataOrigin}. Whenever that status changes, the observer
 * is supposed to invoke {@link #updateCache(eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph)}.
 *
 * @param <I> the type of the identifier of the {@link eu.spitfire.ssp.backend.generic.DataOrigin}
 * @param <D> the type of the {@link eu.spitfire.ssp.backend.generic.DataOrigin}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOriginObserver<I, D extends DataOrigin<I>>{

    private Logger log = LoggerFactory.getLogger(DataOriginObserver.class.getName());

    private BackendComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link DataOriginObserver}.
     *
     * @param componentFactory the {@link BackendComponentFactory} that provides
     *                         all components to handle instances of {@link D}.
     */
    protected DataOriginObserver(BackendComponentFactory<I, D> componentFactory){
        this.componentFactory = componentFactory;
    }


    /**
     * Updates the cache according to the given {@link eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph}.
     *
     * @param expiringNamedGraph the {@link eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph}
     *                           to be used to update the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if the
     * update was successful or with a {@link java.lang.Throwable} if the update failed for some reason.
     */
    public final ListenableFuture<Void> updateCache(final ExpiringNamedGraph expiringNamedGraph){
        LocalServerChannel localChannel = componentFactory.getLocalChannel();
        InternalCacheUpdateRequest updateRequest = new InternalCacheUpdateRequest(expiringNamedGraph);
        Channels.write(localChannel, updateRequest);

        return updateRequest.getCacheUpdateFuture();
    }


//    public final ListenableFuture<Void> updateCache(final SensorValueUpdate sensorValueUpdate){
//        InternalCacheUpdateRequest cacheUpdateTask = new InternalCacheUpdateRequest(sensorValueUpdate);
//        return updateCache(cacheUpdateTask);
//    }


//    private ListenableFuture<Void> updateCache(final InternalCacheUpdateRequest cacheUpdateTask){
//        log.info("Try to update cached status for named graph {}.", cacheUpdateTask.getGraphName());
//
//        final SettableFuture<Void> cacheUpdateFuture = cacheUpdateTask.getCacheUpdateFuture();
//
//        ChannelFuture channelFuture = Channels.write(componentFactory.getLocalChannel(), cacheUpdateTask);
//        channelFuture.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture future) throws Exception {
//                if (!future.isSuccess()){
//                    cacheUpdateFuture.setException(future.getCause());
//                }
//            }
//        });
//
////        Futures.addCallback(cacheUpdateFuture, new FutureCallback<Void>() {
////            @Override
////            public void onSuccess(@Nullable Void result) {
////                log.debug("Successfully updated cached data for graph \"{}\".", cacheUpdateTask.getGraphName());
////            }
////
////            @Override
////            public void onFailure(Throwable t) {
////                log.error(
////                        "Exception while updating cached data for graph \"{}\".", cacheUpdateTask.getGraphName(), t
////                );
////            }
////        });
//
//        return cacheUpdateFuture;
//    }

    /**
     * Returns the {@link DataOriginRegistry} suitable for the instances of
     * {@link eu.spitfire.ssp.backend.generic.DataOrigin} this {@link DataOriginObserver}
     * observes.
     *
     * @return the {@link DataOriginRegistry} suitable for the instances of
     * {@link eu.spitfire.ssp.backend.generic.DataOrigin} this {@link DataOriginObserver}
     * observes.
     */
    protected DataOriginRegistry<I, ? extends D> getRegistry(){
        return this.componentFactory.getRegistry();
    }


    /**
     * Starts the observation of the given {@link eu.spitfire.ssp.backend.generic.DataOrigin}.
     *
     * Whenever the status of the observed {@link eu.spitfire.ssp.backend.generic.DataOrigin} changes, extending
     * classes are supposed to invoke {@link #updateCache(eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph)}.
     *
     * If the observed {@link eu.spitfire.ssp.backend.generic.DataOrigin} is no longer available then extending classes
     * are supposed to invoke {@link DataOriginRegistry#unregisterDataOrigin(DataOrigin)} on the
     * {@link DataOriginRegistry} returned by {@link #getRegistry()}.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to be observed.
     */
    public abstract void startObservation(D dataOrigin);

}

