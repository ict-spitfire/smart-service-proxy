package eu.spitfire.ssp.server.internal.messages.responses;

/**
 * Instances of this class are returned if a modification operation, i.e. status change or deletion, on a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin} was successful.
 *
 * @author Oliver Kleine
 */
public class DataOriginModificationSuccess extends EmptyAccessResult implements DataOriginModificationResult {

    public DataOriginModificationSuccess(String message) {
        super(Code.OK, message);
    }

    @Override
    public String toString() {
        return "[DataOriginModificationSuccess (Message: \"" + getMessage() + "\")]";
    }
}
