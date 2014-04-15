package eu.spitfire.ssp.backends.generic.observation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

public abstract class DataOriginObserver<T>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected BackendComponentFactory<T> componentFactory;

    protected DataOriginObserver(BackendComponentFactory<T> componentFactory){
        this.componentFactory = componentFactory;
    }



    /**
     * Updates the cache according to the given {@link eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus}.
     *
     * @param dataOriginStatus the {@link eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus} to be used to
     *                         update the cache
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if the
     * update was successful or with a {@link java.lang.Throwable} if the update failed for some reason.
     */
    protected final ListenableFuture<Void> updateCache(WrappedDataOriginStatus dataOriginStatus){

        final SettableFuture<Void> resultFuture = SettableFuture.create();

        log.info("Try to update cached status for named graph {}.", dataOriginStatus.getGraphName());
        InternalUpdateCacheMessage updateCacheMessage = new InternalUpdateCacheMessage(dataOriginStatus);

        ChannelFuture future = Channels.write(componentFactory.getLocalChannel(), updateCacheMessage);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    resultFuture.set(null);
                else
                    resultFuture.setException(future.getCause());
            }
        });

        return resultFuture;
    }


    /**
     * Starts the observation of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}. Whenever the status
     * of the observed {@link eu.spitfire.ssp.backends.generic.DataOrigin} changes, implementing classes are supposed
     * to invoke {@link #updateCache(eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus)}.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed.
     */
    public abstract void startObservation(DataOrigin<T> dataOrigin);

}

