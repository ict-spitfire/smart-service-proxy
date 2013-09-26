package eu.spitfire.ssp.backends.coap.observation;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 26.09.13
 * Time: 23:14
 * To change this template use File | Settings | File Templates.
 */
public class InternalObservationTimedOutMessage {

    private URI serviceUri;

    public InternalObservationTimedOutMessage(URI serviceUri){
        this.serviceUri = serviceUri;
    }

    public URI getServiceUri() {
        return serviceUri;
    }
}
