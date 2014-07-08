//package eu.spitfire.ssp.backends.external.coap.registry;
//
//import java.net.InetSocketAddress;
//
///**
// * Exception to be thrown when the discovery of a nodes services fails because of a time-out.
// *
// * @author Oliver Kleine
// */
//public class WellKnownCoreTimeoutException extends Exception{
//
//    private InetSocketAddress nodeAddress;
//
//    /**
//     * @param nodeAddress The {@link InetSocketAddress} of the node whose services where to be discovered
//     */
//    public WellKnownCoreTimeoutException(InetSocketAddress nodeAddress){
//        this.nodeAddress = nodeAddress;
//    }
//
//    /**
//     * Returns the {@link InetSocketAddress} of the node whose services where to be discovered
//     * @return the {@link InetSocketAddress} of the node whose services where to be discovered
//     */
//    public InetSocketAddress getNodeAddress() {
//        return this.nodeAddress;
//    }
//}
