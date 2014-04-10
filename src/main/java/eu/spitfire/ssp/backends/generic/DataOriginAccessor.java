package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveDataOriginMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalUpdateCacheMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link eu.spitfire.ssp.backends.generic.DataOriginAccessor} is a component to access a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class DataOriginAccessor<T> {

    private static Logger log = LoggerFactory.getLogger(DataOriginAccessor.class.getName());

    private LocalServerChannel localChannel;


    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOriginAccessor}
     *
     * @param localChannel the {@link org.jboss.netty.channel.local.LocalServerChannel} that is used to send internal
     *                     messages
     */
    protected DataOriginAccessor(LocalServerChannel localChannel){
        this.localChannel = localChannel;
    }


    /**
     * Returns the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the status from
     *
     * @return the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public abstract WrappedDataOriginStatus getStatus(DataOrigin<T> dataOrigin);


    /**
     * Returns the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} and updates the
     * cache according to the retrieved status
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the status from
     *
     * @return the actual status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public final WrappedDataOriginStatus getStatusAndUpdateCache(DataOrigin<T> dataOrigin){
        WrappedDataOriginStatus wrappedStatus = getStatus(dataOrigin);

        if(wrappedStatus != null)
            updateCache(wrappedStatus);

        return wrappedStatus;
    }

    /**
     * Unregisters the given {@link eu.spitfire.ssp.backends.generic.DataOrigin} from the list of registered data
     * origins and potentially removes the cached data of this data origin from the cache. This method can be called
     * by implementing classes within {@link #getStatus(DataOrigin)} if the access to the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} failed for some reason.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be removed, resp. unregistered
     * @param removeFromCache <code>true</code> if the data from the data origin is supposed to be removed from the
     *                        cache, <code>false</code> otherwise
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} that is set with <code>null</code> if
     * the desired operations were completed successfully or with an {@link java.lang.Throwable} if at least one
     * of the operations failed
     */
    protected final ListenableFuture<Void> removeDataOrigin(DataOrigin<T> dataOrigin, boolean removeFromCache){

        final SettableFuture<Void> resultFuture = SettableFuture.create();

        InternalRemoveDataOriginMessage<T> removeDataOriginMessage =
                new InternalRemoveDataOriginMessage<>(dataOrigin.getIdentifier(), removeFromCache);

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

        ChannelFuture future = Channels.write(this.localChannel, updateCacheMessage);
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
