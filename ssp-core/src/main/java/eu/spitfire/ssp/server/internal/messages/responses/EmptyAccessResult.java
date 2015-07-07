package eu.spitfire.ssp.server.internal.messages.responses;

/**
 * Instances of this class represent the result of an attempt to access a resource either in the
 * {@link eu.spitfire.ssp.server.handler.SemanticCache} or the
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * @author Oliver Kleine
 */
public abstract class EmptyAccessResult extends AccessResult {

    private String message;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult}.
     *
     * @param resultCode the {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult.Code} indicating whether
     *                   the access was successful or not.
     */
    public EmptyAccessResult(Code resultCode, String message) {
        super(resultCode);
        this.message = message;
    }

    /**
     * Returns the message that may contain more detailed information on the result
     * @return the message that may contain more detailed information on the result
     */
    public String getMessage() {
        return message;
    }
}
