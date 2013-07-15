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
    private final String servicePath;
    private final InetAddress targetHostAddress;

    public InternalAbsoluteUriRequest(SettableFuture<URI> uriFuture, String gatewayPrefix, String servicePath,
                                      InetAddress targetHostAddress){
        this.uriFuture = uriFuture;
        this.gatewayPrefix = gatewayPrefix;
        this.servicePath = servicePath;
        this.targetHostAddress = targetHostAddress;
    }

    public InternalAbsoluteUriRequest(SettableFuture<URI> uriFuture, String gatewayPrefix, String servicePath){
        this(uriFuture, gatewayPrefix, servicePath, null);
    }

    public SettableFuture<URI> getUriFuture() {
        return uriFuture;
    }

    public String getGatewayPrefix() {
        return gatewayPrefix;
    }

    public String getServicePath() {
        return servicePath;
    }

    public InetAddress getTargetHostAddress() {
        return targetHostAddress;
    }

    @Override
    public String toString(){
        return "URI request for: " + gatewayPrefix + " (gateway prefix), " + targetHostAddress +
                " (target host address), " + servicePath + " (path)";
    }
}
