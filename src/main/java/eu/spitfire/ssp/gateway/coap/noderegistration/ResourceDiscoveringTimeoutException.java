package eu.spitfire.ssp.gateway.coap.noderegistration;

import java.net.InetSocketAddress;

/**
 * Exception to be thrown when
 */
public class ResourceDiscoveringTimeoutException extends Exception{

    private InetSocketAddress nodeAddress;

    public ResourceDiscoveringTimeoutException(InetSocketAddress nodeAddress){
        this.nodeAddress = nodeAddress;
    }

    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }
}
