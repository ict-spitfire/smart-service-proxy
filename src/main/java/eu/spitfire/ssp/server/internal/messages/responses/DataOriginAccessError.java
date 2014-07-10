package eu.spitfire.ssp.server.internal.messages.responses;

/**
 * Instances of {@link eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError} indicate
 * that the access attempt on a {@link eu.spitfire.ssp.backends.generic.DataOrigin} failed for some reason.
 *
 * @author Oliver Kleine
 */
public class DataOriginAccessError extends EmptyAccessResult implements DataOriginInquiryResult,
        DataOriginModificationResult {


    /**
     * Creates a new instance of {@link eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError}.
     * @param code the {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult.Code} indicating the
     *             reason for the operation to fail.
     * @param errorMessage more detailed information on the reason for the operation to fail
     */
    public DataOriginAccessError(Code code, String errorMessage) {
        super(code, errorMessage);

        if(code.isSuccess())
            throw new IllegalArgumentException("Status code " + code + " is no error code!");
    }

    @Override
    public String toString(){
        return "[DataOriginAccessError (Code: " + getCode() + ", Message: " + getMessage() + "]";
    }
}
