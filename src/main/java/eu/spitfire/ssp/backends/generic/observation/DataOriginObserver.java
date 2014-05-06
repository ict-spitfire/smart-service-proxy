package eu.spitfire.ssp.backends.generic.observation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DataOriginObserver<T>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected BackendComponentFactory<T> componentFactory;

    protected DataOriginObserver(BackendComponentFactory<T> componentFactory){
        this.componentFactory = componentFactory;
    }


    /**
     * Updates the cache according to the given {@link eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph}.
     *
     * @param dataOriginStatus the {@link eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph} to be used to
     *                         update the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if the
     * update was successful or with a {@link java.lang.Throwable} if the update failed for some reason.
     */
    protected final ListenableFuture<Void> updateCache(ExpiringNamedGraphStatusMessage dataOriginStatus){

        final SettableFuture<Void> resultFuture = SettableFuture.create();
        log.info("Try to update cached status for named graph {}.", dataOriginStatus.getExpiringGraph().getGraphName());

        Channels.write(componentFactory.getLocalChannel(), dataOriginStatus)
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess())
                            resultFuture.set(null);
                        else
                            resultFuture.setException(future.getCause());
                    }
                });

        return resultFuture;
    }


//    protected final ListenableFuture<Void> removeDataOrigin(DataOrigin<T> dataOrigin){
//        final SettableFuture<Void> removalFuture = SettableFuture.create();
//
//        log.info("Try to remove data origin: \"{}\".", dataOrigin);
//        DataOriginRemovalMessage<T> removalMessage = new DataOriginRemovalMessage<>(dataOrigin);
//
//        Channels.write(componentFactory.getLocalChannel(), removalMessage)
//                .addListener(new ChannelFutureListener() {
//                    @Override
//                    public void operationComplete(ChannelFuture future) throws Exception {
//                        if(future.isSuccess())
//                            removalFuture.set(null);
//                        else
//                            removalFuture.setException(future.getCause());
//                    }
//                });
//
//        return removalFuture;
//    }

    /**
     * Starts the observation of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}. Whenever the status
     * of the observed {@link eu.spitfire.ssp.backends.generic.DataOrigin} changes, implementing classes are supposed
     * to invoke {@link #updateCache(eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph)}.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed.
     */
    public abstract void startObservation(DataOrigin<T> dataOrigin);

}

