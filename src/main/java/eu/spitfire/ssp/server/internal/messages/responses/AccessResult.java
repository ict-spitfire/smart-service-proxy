package eu.spitfire.ssp.server.internal.messages.responses;

/**
 *
 * @author Oliver Kleine
 */
public abstract class AccessResult {

    public enum Code {
        OK(200), BAD_REQUEST(400), NOT_FOUND(404), NOT_ALLOWED(405), INTERNAL_ERROR(500);

        private int codeNumber;

        private Code(int codeNumber){
            this.codeNumber = codeNumber;
        }

        public int getCodeNumber(){
            return this.codeNumber;
        }

        public boolean isSuccess(){
            return this.codeNumber == 200;
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
    public Code getCode() {
        return this.resultCode;
    }

    @Override
    public abstract String toString();
}
