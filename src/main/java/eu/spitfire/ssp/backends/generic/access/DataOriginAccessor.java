package eu.spitfire.ssp.backends.generic.access;

import com.google.common.util.concurrent.ListenableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DataOriginAccessor} is a component to access a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class DataOriginAccessor<T> {

    public static final long MILLIS_PER_YEAR = 31556952000L;
    private static Logger log = LoggerFactory.getLogger(DataOriginAccessor.class.getName());

    protected BackendComponentFactory<T> componentFactory;


    /**
     * Creates a new instance of {@link DataOriginAccessor}
     */
    protected DataOriginAccessor(BackendComponentFactory<T> componentFactory){
        this.componentFactory = componentFactory;
    }


//    /**
//     * This method invokes {@link #getGraph(eu.spitfire.ssp.backends.generic.DataOrigin)} and awaits the status
//     * returned. Then it sends the status to the client.
//     *
//     * @param channel the {@link org.jboss.netty.channel.Channel} on which the result is supposed to be written
//     * @param clientAddress the {@link java.net.InetSocketAddress} of the client that aims to retireve the data origins
//     *                      status
//     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} which is to be queried for its actual
//     *                   status
//     * @param removeOnFailure <code>true</code> is the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} is
//     *                        supposed to be removed from the proxy if some error occurs while trying to retrieve its
//     *                        actual status
//     */
//    public final ListenableFuture<Void> retrieveStatus(final Channel channel,
//            final InetSocketAddress clientAddress, final DataOrigin<T> dataOrigin, final boolean removeOnFailure){
//
//        final SettableFuture<Void> resultFuture = SettableFuture.create();
//
//        ListenableFuture<ExpiringNamedGraph> statusFuture = getGraph(dataOrigin);
//        Futures.addCallback(statusFuture, new FutureCallback<ExpiringNamedGraph>() {
//
//            @Override
//            public void onSuccess(ExpiringNamedGraph dataOriginStatus) {
//                ExpiringNamedGraphStatusMessage statusMessage = new ExpiringNamedGraphStatusMessage(dataOriginStatus);
//                Channels.write(channel, statusMessage, clientAddress);
//                resultFuture.set(null);
//            }
//
//            @Override
//            public void onFailure(Throwable throwable) {
//
//                if(removeOnFailure){
//                    Futures.addCallback(removeDataOrigin(dataOrigin), new FutureCallback<Void>() {
//
//                        @Override
//                        public void onSuccess(Void aVoid) {
//                            log.info("Successfully removed data origin {}", dataOrigin.getIdentifier());
//                        }
//
//                        @Override
//                        public void onFailure(Throwable throwable) {
//                            log.error("Removal of data origin {} failed!", dataOrigin.getIdentifier(), throwable);
//                        }
//
//                    }, backendTasksExecutorService);
//                }
//
//                resultFuture.setException(throwable);
//            }
//
//        }, backendTasksExecutorService);
//
//        return resultFuture;
//    }


//    public abstract ListenableFuture<ExpiringNamedGraph> getGraph(DataOrigin<T> dataOrigin)
//            throws DataOriginAccessException;

    /**
     * Returns a future that is to be set with actual status of the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} upon reception of the status. If some error
     * occurs while accessing the data origin set the returned future with an instance of
     * {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessException}.
     *
     * @param identifier the {@link T} of the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the
     *                   status from
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the the actual
     * {@link eu.spitfire.ssp.backends.generic.ExpiringNamedGraph} retrieved retrieved from the given given
     * identifier of a {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public abstract ListenableFuture<ExpiringNamedGraph> getStatus(T identifier) throws DataOriginAccessException;


    public abstract ListenableFuture<Boolean> setStatus(T identifier, Model status) throws DataOriginAccessException;


    public abstract ListenableFuture<Boolean> deleteDataOrigin(T identifier) throws DataOriginAccessException;

}
