package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * A {@link Accessor} is a component to access a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class Accessor<T> {

//    public static final long MILLIS_PER_YEAR = 31556952000L;

    private BackendComponentFactory<T> componentFactory;

    /**
     * Creates a new instance of {@link Accessor}
     */
    protected Accessor(BackendComponentFactory<T> componentFactory){
        this.componentFactory = componentFactory;
    }


    protected BackendComponentFactory<T> getComponentFactory(){
        return this.componentFactory;
    }

    /**
     * Returns a future that is to be set with actual status of the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} upon reception of the status. If some error
     * occurs while accessing the data origin set the returned future with an instance of
     * {@link java.lang.Exception}.
     *
     * Extending classes are supposed to override this method to support status retrieval.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to retrieve the status from
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the the actual
     * {@link eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph} retrieved retrieved from the given given
     * identifier of a {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public ListenableFuture<GraphStatusMessage> getStatus(DataOrigin<T> dataOrigin){
        return createOperationNotSupportedMessage("status retrieval");
    }

    /**
     * Extending classes are supposed to override this method to support status changes.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to change the status of
     * @param status the supposed status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of
     * the status change attempt which is either a
     * {@link eu.spitfire.ssp.server.common.messages.ExpiringGraphStatusMessage} if the operation was successful or a
     * {@link eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage} if the operation failed.
     */
    public ListenableFuture<GraphStatusMessage> setStatus(DataOrigin<T> dataOrigin, Model status){
        return createOperationNotSupportedMessage("status changes");
    }

    /**
     * Extending classes are supposed to override this method to support resource deletion.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be deleted, i.e. actually
     *                   {@link eu.spitfire.ssp.backends.generic.DataOrigin#getIdentifier()} is the thing
     *                   to be deleted.
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of
     * the deletion attempt which is either a
     * {@link eu.spitfire.ssp.server.common.messages.EmptyGraphStatusMessage} if the operation was successful or a
     * {@link eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage} if the operation failed.
     */
    public ListenableFuture<GraphStatusMessage> deleteResource(DataOrigin<T> dataOrigin){
        return createOperationNotSupportedMessage("resource deletion");
    }


    private ListenableFuture<GraphStatusMessage> createOperationNotSupportedMessage(String operationName){
        SettableFuture<GraphStatusMessage> graphStatusFuture = SettableFuture.create();

        graphStatusFuture.set(new GraphStatusErrorMessage(
            HttpResponseStatus.METHOD_NOT_ALLOWED, "Data Origin Accessor does not support " + operationName + "."
        ));

        return graphStatusFuture;
    }

}
