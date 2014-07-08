package eu.spitfire.ssp.server.internal.messages;

/**
 *
 * @author Oliver Kleine
 */
public abstract class AccessResult {

    public enum Code {
        OK(200), NOT_FOUND(404), NOT_ALLOWED(405), INTERNAL_ERROR(500);

        private int codeNumber;

        private Code(int codeNumber){
            this.codeNumber = codeNumber;
        }

        public int getCodeNumber(){
            return this.codeNumber;
        }

        public boolean isErrorCode(){
            return this.codeNumber > 400;
        }
    }

    private Code resultCode;

    /**
     * Creates a new instance of {@link AccessResult}.
     *
     * @param resultCode the {@link AccessResult.Code} indicating whether
     * the access was successful or not.
     */
    public AccessResult(Code resultCode){
        this.resultCode = resultCode;
    }

    /**
     * Returns the {@link AccessResult.Code} indicating whether the access
     * was successful or not.
     *
     * @return the {@link AccessResult.Code} indicating whether the access
     * was successful or not.
     */
    public Code getStatusCode() {
        return this.resultCode;
    }

    @Override
    public abstract String toString();
}
