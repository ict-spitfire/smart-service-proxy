package eu.spitfire.ssp.proxyservicemanagement.coap.observation;

import java.net.Inet6Address;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.04.13
 * Time: 12:02
 * To change this template use File | Settings | File Templates.
 */
public class ObservingFailedMessage {

    private Inet6Address serviceHost;
    private String servicePath;

    public ObservingFailedMessage(Inet6Address serviceHost, String servicePath){
        this.serviceHost = serviceHost;
        this.servicePath = servicePath;
    }

    public Inet6Address getServiceHost() {
        return serviceHost;
    }

    public String getServicePath() {
        return servicePath;
    }

}
