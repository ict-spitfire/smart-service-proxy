package eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import org.apache.log4j.Logger;
import sun.net.util.IPAddressUtil;

import javax.annotation.Nullable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 12.04.13
 * Time: 15:41
 * To change this template use File | Settings | File Templates.
 */
//Handles the registration process for new nodes in a new thread
class CoapResourceDiscoverer extends CoapClientApplication implements Callable<Boolean> {

    Logger log = Logger.getLogger(CoapResourceDiscoverer.class.getName());

    private Inet6Address remoteAddress;

    private Object monitor = new Object();
    private CoapResponse coapResponse;

    public CoapResourceDiscoverer(InetAddress remoteAddress){
        super();
        this.remoteAddress = (Inet6Address) remoteAddress;
    }

    @Override
    public Boolean call(){
        coapResponse = null;
        try{
            log.debug("Discover resources from " + remoteAddress.getHostAddress());
            CoapBackend coapBackend = getResponsibleCoapBackend(remoteAddress);

            if(coapBackend == null){
                return false;
            }

            //Delete possibly already registered services of the sensornode
            Set<Inet6Address> addressList = coapBackend.getSensorNodes();

            if(addressList.contains(remoteAddress)){
                log.debug("New here_i_am message from " + remoteAddress + ", delete old resources.");
                coapBackend.deleteServices(remoteAddress);
            }

            //Create request for /.well-known/core resource
            CoapRequest request = createRequestForWellKnownCore();

            //Send request for /.well-known/core
            sendRequestForWellKnownCore(request);

            //Process the response
            coapBackend.processWellKnownCoreResource(coapResponse, remoteAddress);

            return true;
        }
        catch(Exception e){
            log.error("Error while discovering resources from " + remoteAddress.getHostAddress(), e);
            return false;
        }
    }

    @Nullable
    private CoapRequest createRequestForWellKnownCore(){
        try{
            //Send request to the .well-known/core resource of the new sensornode
            String remoteIP = remoteAddress.getHostAddress();

            //Remove eventual scope ID
            if(remoteIP.indexOf("%") != -1){
                remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
            }
            if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
                remoteIP = "[" + remoteIP + "]";
            }
            URI targetURI = new URI("coap://" + remoteIP + ":5683/.well-known/core");
            return new CoapRequest(MsgType.CON, Code.GET, targetURI, this);
        }
        catch(Exception e){
            log.error("Could not create request for .well-known/core resource.", e);
            return null;
        }
    }

    private void sendRequestForWellKnownCore(CoapRequest coapRequest){
        CoapResponse coapResponse = null;
        try {
            synchronized (monitor){
                //Write request for .well-knwon/core
                writeCoapRequest(coapRequest);
                if(log.isDebugEnabled()){
                    log.debug("Request for /.well-known/core resource at: " + remoteAddress.getHostAddress() +
                            " written.");
                }

                //Wait for the response
                while(coapResponse == null){
                    monitor.wait();
                }
            }
        }
        catch(Exception e){
            log.error("Error while trying to discover new resources.", e);
        }
    }

    @Nullable
    private CoapBackend getResponsibleCoapBackend(Inet6Address remoteAddress){
        for(CoapBackend backend : CoapNodeRegistrationServer.getInstance().getCoapBackends()){
            log.debug("Check if backend for prefix " + backend.getPrefix() + " is responsible.");

            //Prefix is an IP address
            if(remoteAddress.getHostAddress().startsWith(backend.getPrefix())){
                log.debug("Backend found for address " + remoteAddress.getHostAddress());
                return backend;
            }
            //Prefix is a DNS name
            else{
                if((remoteAddress.getHostName()).equals(backend.getPrefix())){
                    log.debug("Backend found for DNS name " + remoteAddress.getHostName());
                    return backend;
                }
            }
            log.debug("Backend for prefix " + backend.getPrefix() + " is not responsible.");
        }

        log.debug("No backend found for IP address: " + remoteAddress.getHostAddress());
        return null;
    }

    @Override
    public void receiveResponse(CoapResponse coapResponse) {
        log.debug("Received response for .well-known/core");
        synchronized (monitor){
            this.coapResponse = coapResponse;
            monitor.notify();
        }
    }

    @Override
    public void receiveEmptyACK() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleRetransmissionTimout() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
