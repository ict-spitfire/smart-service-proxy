package eu.spitfire.ssp.server.internal.exception;

/**
 * Created by olli on 21.08.15.
 */
public class OperationTimeoutException extends Exception{

    public OperationTimeoutException(String message){
        super(message);
    }

}
