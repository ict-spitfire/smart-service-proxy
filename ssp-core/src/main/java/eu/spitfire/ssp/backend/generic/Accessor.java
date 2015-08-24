package eu.spitfire.ssp.backend.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.message.exception.OperationNotSupportedException;

import org.apache.jena.rdf.model.Model;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * A {@link Accessor} is a component to access a
 * {@link eu.spitfire.ssp.backend.generic.DataOrigin}, i.e. retrieve its actual status.
 *
 * @link Oliver Kleine
 */
public abstract class Accessor<I, D extends DataOrigin<I>> {


    private ComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link Accessor}
     */
    protected Accessor(ComponentFactory<I, D> componentFactory){
        this.componentFactory = componentFactory;
    }


    protected ComponentFactory<I, D> getComponentFactory(){
        return this.componentFactory;
    }

    /**
     * Returns a future that is to be set with actual status of the given
     * {@link eu.spitfire.ssp.backend.generic.DataOrigin} upon reception of the status. If some error
     * occurs while accessing the data origin set the returned future with an instance of
     * {@link java.lang.Exception}.
     *
     * Extending classes are supposed to override this method to support status retrieval.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to retrieve the status from
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the the actual
     * {@link eu.spitfire.ssp.server.internal.ExpiringNamedGraph} retrieved retrieved from the given given
     * identifier of a {@link eu.spitfire.ssp.backend.generic.DataOrigin}.
     */
    public ListenableFuture<ExpiringNamedGraph> getStatus(D dataOrigin){
        SettableFuture<ExpiringNamedGraph> result = SettableFuture.create();
        result.setException(new OperationNotSupportedException("Accessor for " + dataOrigin.getGraphName() +
                " does not support GET (not implemented!"));
        return result;
    }

    /**
     * Extending classes are supposed to override this method to support status changes.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to change the status of
     * @param status the supposed status of the given {@link eu.spitfire.ssp.backend.generic.DataOrigin}
     *
     * @return  a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of
     * the deletion attempt, i.e. an instance of
     * {@link org.jboss.netty.handler.codec.http.HttpResponse}.
     */
    public ListenableFuture<ModificationResult> setStatus(D dataOrigin, Model status){
        SettableFuture<ModificationResult> result = SettableFuture.create();
        result.setException(new OperationNotSupportedException("Accessor for " + dataOrigin.getGraphName() +
                " does not support UPDATE (not implemented!"));
        return result;
    }

    /**
     * Extending classes are supposed to override this method to support resource deletion.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to be deleted, i.e. actually
     *                   {@link eu.spitfire.ssp.backend.generic.DataOrigin#getIdentifier()} is the thing
     *                   to be deleted.
     *
     * @return a {@link com.google.common.util.concurrent.ListenableFuture} to be set with the result of
     * the deletion attempt, i.e. an instance of
     * {@link org.jboss.netty.handler.codec.http.HttpResponse}
     */
    public ListenableFuture<ModificationResult> deleteResource(D dataOrigin){
        SettableFuture<ModificationResult> result = SettableFuture.create();
        result.setException(new OperationNotSupportedException("Accessor for " + dataOrigin.getGraphName() +
                " does not support DELETE (not implemented!"));
        return result;
    }


//    private ListenableFuture<DataOriginAccessError> createOperationNotSupportedMessage(String operationName){
//        SettableFuture<DataOriginAccessError> accessResultFuture = SettableFuture.create();
//
//        accessResultFuture.set(new DataOriginAccessError(
//                AccessResult.Code.NOT_ALLOWED, "Data Origin Accessor does not support " + operationName + "."
//        ));
//
//        return accessResultFuture;
//    }


    public enum ModificationResult{CREATED, UPDATED, DELETED}

}
