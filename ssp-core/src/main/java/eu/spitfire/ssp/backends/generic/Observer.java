package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.messages.requests.InternalCacheUpdateTask;
import eu.spitfire.ssp.server.internal.messages.requests.SensorValueUpdate;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Observer} is the component to observe the status of one or more instances of
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}. Whenever that status changes, the observer
 * is supposed to invoke {@link #updateCache(eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph)}.
 *
 * @param <I> the type of the identifier of the {@link eu.spitfire.ssp.backends.generic.DataOrigin}
 * @param <D> the type of the {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * @author Oliver Kleine
 */
public abstract class Observer<I, D extends DataOrigin<I>>{

    private Logger log = LoggerFactory.getLogger(Observer.class.getName());

    private BackendComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.Observer}.
     *
     * @param componentFactory the {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory} that provides
     *                         all components to handle instances of {@link D}.
     */
    protected Observer(BackendComponentFactory<I, D> componentFactory){
        this.componentFactory = componentFactory;
    }


    /**
     * Updates the cache according to the given
     * {@link eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph}.
     *
     * @param expiringNamedGraph the {@link eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph}
     *                           to be used to update the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if the
     * update was successful or with a {@link java.lang.Throwable} if the update failed for some reason.
     */
    public final ListenableFuture<Void> updateCache(final ExpiringNamedGraph expiringNamedGraph){
        InternalCacheUpdateTask cacheUpdateTask = new InternalCacheUpdateTask(expiringNamedGraph);
        return updateCache(cacheUpdateTask);
    }


    public final ListenableFuture<Void> updateCache(final SensorValueUpdate sensorValueUpdate){
        InternalCacheUpdateTask cacheUpdateTask = new InternalCacheUpdateTask(sensorValueUpdate);
        return updateCache(cacheUpdateTask);
    }


    private ListenableFuture<Void> updateCache(final InternalCacheUpdateTask cacheUpdateTask){
        log.info("Try to update cached status for named graph {}.", cacheUpdateTask.getGraphName());

        final SettableFuture<Void> cacheUpdateFuture = cacheUpdateTask.getCacheUpdateFuture();

        ChannelFuture channelFuture = Channels.write(componentFactory.getLocalChannel(), cacheUpdateTask);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()){
                    cacheUpdateFuture.setException(future.getCause());
                }
            }
        });

//        Futures.addCallback(cacheUpdateFuture, new FutureCallback<Void>() {
//            @Override
//            public void onSuccess(@Nullable Void result) {
//                log.debug("Successfully updated cached data for graph \"{}\".", cacheUpdateTask.getGraphName());
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                log.error(
//                        "Exception while updating cached data for graph \"{}\".", cacheUpdateTask.getGraphName(), t
//                );
//            }
//        });

        return cacheUpdateFuture;
    }

    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.Registry} suitable for the instances of
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} this {@link eu.spitfire.ssp.backends.generic.Observer}
     * observes.
     *
     * @return the {@link eu.spitfire.ssp.backends.generic.Registry} suitable for the instances of
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} this {@link eu.spitfire.ssp.backends.generic.Observer}
     * observes.
     */
    protected Registry<I, ? extends D> getRegistry(){
        return this.componentFactory.getRegistry();
    }


    /**
     * Starts the observation of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     *
     * Whenever the status of the observed {@link eu.spitfire.ssp.backends.generic.DataOrigin} changes, extending
     * classes are supposed to invoke {@link #updateCache(eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph)}.
     *
     * If the observed {@link eu.spitfire.ssp.backends.generic.DataOrigin} is no longer available then extending classes
     * are supposed to invoke {@link eu.spitfire.ssp.backends.generic.Registry#unregisterDataOrigin(DataOrigin)} on the
     * {@link eu.spitfire.ssp.backends.generic.Registry} returned by {@link #getRegistry()}.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed.
     */
    public abstract void startObservation(D dataOrigin);

}

