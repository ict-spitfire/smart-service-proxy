package eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import org.apache.log4j.Logger;
import sun.net.util.IPAddressUtil;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
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
        System.out.println("LOOOOS!!");
        CoapBackend coapBackend = null;

        for(CoapBackend backend : CoapNodeRegistrationServer.getInstance().getCoapBackends()){
            log.debug("remoteAddress.getHostAddress(): " + remoteAddress.getHostAddress());
            log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());

            //Prefix is an IP address
            if(remoteAddress.getHostAddress().startsWith(backend.getPrefix())){
                coapBackend = backend;
                log.debug("Backend found for address " + remoteAddress.getHostAddress());
                break;
            }
            //Prefix is a DNS name
            else{
                log.debug("Look up backend for DNS name " + remoteAddress.getHostName());
                log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());
                if((remoteAddress.getHostName()).equals(backend.getPrefix())){
                    coapBackend = backend;
                    log.debug("Backend found for DNS name " + remoteAddress.getHostName());
                    break;
                }
            }
        }

        if(coapBackend == null){
            log.debug("No backend found for IP address: " + remoteAddress.getHostAddress());
            return false;
        }

        //Only register new nodes (avoid duplicates)
        Set<Inet6Address> addressList = coapBackend.getSensorNodes();

        if(addressList.contains(remoteAddress)){
            log.debug("New here_i_am message from " + remoteAddress + ".");
            coapBackend.deleteServices(remoteAddress);
        }

        try {
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
            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI, this);

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

                //Process the response
                coapBackend.processWellKnownCoreResource(coapResponse, remoteAddress);
            }
            return true;

        } catch (InvalidMessageException e) {
            log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            return false;
        } catch (ToManyOptionsException e) {
            log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            return false;
        } catch (InvalidOptionException e) {
            log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            return false;
        } catch (URISyntaxException e) {
            log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            return false;
        } catch (InterruptedException e) {
            log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            return false;
        }
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
