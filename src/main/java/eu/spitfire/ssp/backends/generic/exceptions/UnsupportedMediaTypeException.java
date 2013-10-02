package eu.spitfire.ssp.backends.generic.exceptions;

/**
 * Exception to be thrown if the content type of a message could not be processed, because the media type is either
 * unknown or not supported.
 *
 * @author Oliver Kleine
 */
public class UnsupportedMediaTypeException extends Exception{

    private String mediaType;

    public UnsupportedMediaTypeException(String mediaType){
        this.mediaType = mediaType;
    }

    private String getMediaType(){
        return this.mediaType;
    }

    public String toString(){
        return "Unsupported media type: " + mediaType;
    }
}
