package eu.spitfire.ssp.server.internal.messages.responses;

/**
 * Interface to be implemented by classes that represent the result of a modification operation, i.e.
 * status change or deletion, of a {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * @author Oliver Kleine
*/
public interface DataOriginModificationResult {

    /**
     * Returns the {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult.Code} that indicates if
     * the operation was successful or not.
     *
     * @return the {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult.Code} that indicates if
     * the operation was successful or not.
     */
    public AccessResult.Code getCode();

}
