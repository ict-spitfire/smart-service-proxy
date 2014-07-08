package eu.spitfire.ssp.server.internal.messages;

/**
* Created by olli on 07.07.14.
*/
public abstract class SuccessfulAccessResult extends AccessResult{

    public SuccessfulAccessResult(Code resultCode) {
        super(resultCode);

        if(resultCode.isErrorCode())
            throw new IllegalArgumentException("Code " + resultCode + " is an error code!");
    }
}
