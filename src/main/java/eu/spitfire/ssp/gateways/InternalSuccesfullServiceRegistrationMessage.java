package eu.spitfire.ssp.gateways;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
public class InternalSuccesfullServiceRegistrationMessage {

    private URI serviceUri;

    public InternalSuccesfullServiceRegistrationMessage(URI serviceUri) {
        this.serviceUri = serviceUri;
    }

    public URI getServiceUri() {
        return serviceUri;
    }
}
