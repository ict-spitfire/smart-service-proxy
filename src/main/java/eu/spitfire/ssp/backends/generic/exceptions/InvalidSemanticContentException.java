package eu.spitfire.ssp.backends.generic.exceptions;

/**
 * Exception to be thrown if the semantic payload contained in a message could not be processed.
 *
 * @Oliver Kleine
 */
public class InvalidSemanticContentException extends Exception {

    /**
     * @param cause the {@link Throwable} indicating the reason why the payload could not be processed
     */
    public InvalidSemanticContentException(Throwable cause){
        super(cause);
    }
}
