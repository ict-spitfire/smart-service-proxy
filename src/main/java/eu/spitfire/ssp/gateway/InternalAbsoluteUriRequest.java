package eu.spitfire.ssp.gateway;

import com.google.common.util.concurrent.SettableFuture;

import java.net.InetAddress;
import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.07.13
 * Time: 15:58
 * To change this template use File | Settings | File Templates.
 */
public class InternalAbsoluteUriRequest {

    private final SettableFuture<URI> uriFuture;
    private final String gatewayPrefix;
    private final URI serviceUri;

    public InternalAbsoluteUriRequest(SettableFuture<URI> uriFuture, String gatewayPrefix, URI serviceUri){
        this.uriFuture = uriFuture;
        this.gatewayPrefix = gatewayPrefix;
        this.serviceUri = serviceUri;
    }

    public SettableFuture<URI> getUriFuture() {
        return uriFuture;
    }

    public String getGatewayPrefix() {
        return gatewayPrefix;
    }

    public URI getServiceUri() {
        return serviceUri;
    }

    @Override
    public String toString(){
        return "URI request from " + getGatewayPrefix() + " (gateway name) for: " + getServiceUri();
    }
}
