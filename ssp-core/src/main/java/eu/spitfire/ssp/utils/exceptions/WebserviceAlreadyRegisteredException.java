package eu.spitfire.ssp.utils.exceptions;

import java.net.URI;

/**
 * Created by olli on 15.04.14.
 */
public class WebserviceAlreadyRegisteredException extends Exception {

    private URI webserviceURI;

    public WebserviceAlreadyRegisteredException(URI webserviceURI){
        super("Webservice " + webserviceURI + " was already registered!");
        this.webserviceURI = webserviceURI;
    }

    public URI getWebserviceURI(){
        return this.webserviceURI;
    }

}
