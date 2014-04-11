package eu.spitfire.ssp.backends.generic.access;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.server.webservice.WrappedResourceStatus;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveDataOriginMessage;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link DataOriginAccessor} is a component to access a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class DataOriginAccessor<T> {

    private static Logger log = LoggerFactory.getLogger(DataOriginAccessor.class.getName());

    protected final LocalServerChannel localChannel;
    protected ScheduledExecutorService executorService;


    /**
     * Creates a new instance of {@link DataOriginAccessor}
     */
    protected DataOriginAccessor(LocalServerChannel localChannel, ScheduledExecutorService executorService){
        this.localChannel = localChannel;
        this.executorService = executorService;
    }


    /**
     * This method invokes {@link #getStatus(eu.spitfire.ssp.backends.generic.DataOrigin)} and awaits the status
     * returned. Then it sends the status to the client.
     *
     * @param channel the {@link org.jboss.netty.channel.Channel} on which the result is supposed to be written
     * @param clientAddress the {@link java.net.InetSocketAddress} of the client that aims to retireve the data origins
     *                      status
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} which is to be queried for its actual
     *                   status
     * @param removeOnFailure <code>true</code> is the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} is
     *                        supposed to be removed from the proxy if some error occurs while trying to retrieve its
     *                        actual status
     */
    public final ListenableFuture<Void> retrieveStatus(final Channel channel,
            final InetSocketAddress clientAddress, final DataOrigin<T> dataOrigin, final boolean removeOnFailure){

        final SettableFuture<Void> resultFuture = SettableFuture.create();

        ListenableFuture<WrappedDataOriginStatus> statusFuture = getStatus(dataOrigin);
        Futures.addCallback(statusFuture, new FutureCallback<WrappedDataOriginStatus>() {

            @Override
            public void onSuccess(WrappedDataOriginStatus dataOriginStatus) {
                InternalDataOriginStatusMessage statusMessage = new InternalDataOriginStatusMessage(dataOriginStatus);
                Channels.write(channel, statusMessage, clientAddress);
                resultFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable) {

                if(removeOnFailure){
                    Futures.addCallback(removeDataOrigin(dataOrigin), new FutureCallback<Void>() {

                        @Override
                        public void onSuccess(Void aVoid) {
                            log.info("Successfully removed data origin {}", dataOrigin.getIdentifier());
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.error("Removal of data origin {} failed!", dataOrigin.getIdentifier(), throwable);
                        }

                    }, executorService);
                }

                resultFuture.setException(throwable);
            }

        }, executorService);

        return resultFuture;
    }

    /**
     * Returns the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the status from
     *
     * @return the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    abstract ListenableFuture<WrappedDataOriginStatus> getStatus(DataOrigin<T> dataOrigin);


//    /**
//     * Returns the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} and updates the
//     * cache according to the retrieved status
//     *
//     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the status from
//     *
//     * @return the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
//     */
//    public final ListenableFuture<WrappedDataOriginStatus> getStatusAndUpdateCache(final DataOrigin<T> dataOrigin){
//        ListenableFuture<WrappedDataOriginStatus> statusFuture = retrieveStatus(dataOrigin);
//
//        Futures.addCallback(statusFuture, new FutureCallback<WrappedDataOriginStatus>() {
//
//            @Override
//            public void onSuccess(WrappedDataOriginStatus dataOriginStatus) {
//                if(dataOriginStatus != null)
//                    updateCache(dataOriginStatus);
//            }
//
//            @Override
//            public void onFailure(Throwable throwable) {
//                log.error("Error while trying to retrieve status from data origin with identifier {}!",
//                        dataOrigin.getIdentifier());
//            }
//        });
//
//        return statusFuture;
//    }


    /**
     * Unregisters the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} from the list of registered data
     * origins and potentially removes the cached data of this data origin from the cache. This method can be called
     * by implementing classes within {@link #getStatus(DataOrigin)} if the access to the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} failed for some reason.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be removed, resp. unregistered
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if
     * the desired operations were completed successfully or with an {@link java.lang.Throwable} if at least one
     * of the operations failed
     */
    protected final ListenableFuture<Void> removeDataOrigin(DataOrigin<T> dataOrigin){

        final SettableFuture<Void> resultFuture = SettableFuture.create();

        InternalRemoveDataOriginMessage<T> removeDataOriginMessage =
                new InternalRemoveDataOriginMessage<>(dataOrigin.getIdentifier());

        ChannelFuture future = Channels.write(localChannel, removeDataOriginMessage);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    resultFuture.set(null);
                else
                    future.setFailure(future.getCause());
            }
        });

        return resultFuture;
    }




//    /**
//     * Splits the given {@link com.hp.hpl.jena.rdf.model.Model} into several {@link com.hp.hpl.jena.rdf.model.Model}
//     * instances, one for each subject contained in the given model.
//     *
//     * @param model a {@link com.hp.hpl.jena.rdf.model.Model} instance to be split up into models per subject
//     *
//     * @return a {@link java.util.Map} containing the subjects of the given model as keys and the appropriate model
//     * as value
//     */
//    public static Map<URI, Model> getModelsPerSubject(Model model){
//        Map<URI, Model> result = new HashMap<>();
//
//        //Iterate over all subjects in the Model
//        ResIterator subjectIterator = model.listSubjects();
//        while(subjectIterator.hasNext()){
//            Resource resource = subjectIterator.next();
//
//            Model subModel = ModelFactory.createDefaultModel();
//
//            //Iterate over all properties fort the actual subject
//            StmtIterator stmtIterator = resource.listProperties();
//            while(stmtIterator.hasNext()){
//                subModel = subModel.add(stmtIterator.next());
//            }
//
//            try{
//                result.put(new URI(resource.getURI()), subModel);
//            }
//            catch(URISyntaxException e){
//                log.error("Malformed Resource URI!", e);
//            }
//        }
//
//        return result;
//    }



}
