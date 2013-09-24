package eu.spitfire.ssp.backends.utils;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 15:52
 * To change this template use File | Settings | File Templates.
 */
public class UnsupportedMediaTypeException extends Exception{

    private String mediaType;

    public UnsupportedMediaTypeException(String mediaType){
        this.mediaType = mediaType;
    }

    public String toString(){
        return "Unsupported media type: " + mediaType;
    }
}
