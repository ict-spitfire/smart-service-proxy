package eu.spitfire.ssp.backends.coap.noderegistration;

import java.net.InetSocketAddress;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 28.06.13
 * Time: 19:26
 * To change this template use File | Settings | File Templates.
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
