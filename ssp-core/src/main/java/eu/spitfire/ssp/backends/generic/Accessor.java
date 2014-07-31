package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError;
import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginModificationResult;

/**
 * A {@link Accessor} is a component to access a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class Accessor<I, D extends DataOrigin<I>> {


    private BackendComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link Accessor}
     */
    protected Accessor(BackendComponentFactory<I, D> componentFactory){
        this.componentFactory = componentFactory;
    }


    protected BackendComponentFactory<I, D> getComponentFactory(){
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
     * {@link eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph} retrieved retrieved from the given given
     * identifier of a {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public ListenableFuture<? extends DataOriginInquiryResult> getStatus(D dataOrigin){
        return createOperationNotSupportedMessage("status retrieval");
    }

    /**
     * Extending classes are supposed to override this method to support status changes.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to change the status of
     * @param status the supposed status of the given {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @return  a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of
     * the deletion attempt, i.e. an instance of
     * {@link eu.spitfire.ssp.server.internal.messages.responses.DataOriginModificationResult}.
     */
    public ListenableFuture<? extends DataOriginModificationResult> setStatus(D dataOrigin, Model status){
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
     * the deletion attempt, i.e. an instance of
     * {@link eu.spitfire.ssp.server.internal.messages.responses.DataOriginModificationResult}
     */
    public ListenableFuture<? extends DataOriginModificationResult> deleteResource(D dataOrigin){
        return createOperationNotSupportedMessage("resource deletion");
    }


    private ListenableFuture<DataOriginAccessError> createOperationNotSupportedMessage(String operationName){
        SettableFuture<DataOriginAccessError> accessResultFuture = SettableFuture.create();

        accessResultFuture.set(new DataOriginAccessError(
                AccessResult.Code.NOT_ALLOWED, "Data Origin Accessor does not support " + operationName + "."
        ));

        return accessResultFuture;
    }

}
