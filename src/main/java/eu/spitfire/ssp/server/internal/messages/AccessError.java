package eu.spitfire.ssp.server.internal.messages;

import eu.spitfire.ssp.backends.DataOriginModificationResult;

/**
* Created by olli on 28.04.14.
*/
public class AccessError extends AccessResult implements DataOriginModificationResult {

    private String errorMessage;

    public AccessError(Code errorCode, String errorMessage) {
        super(errorCode);

        if(!errorCode.isErrorCode())
            throw new IllegalArgumentException("Status code " + errorCode + " is no error code!");

        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString(){
        return "[AccessError (HTTP Code: " + getStatusCode() + ", Message: " + getErrorMessage() + "]";
    }
}
