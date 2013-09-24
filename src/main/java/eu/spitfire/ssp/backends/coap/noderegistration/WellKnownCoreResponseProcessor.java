package eu.spitfire.ssp.backends.coap.noderegistration;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Set;
import java.util.TreeSet;

/**
 * Instance of {@link CoapResponseProcessor} to process incoming responses from <code>.well-known/core</code> CoAP resources.
 *
 * @author Oliver Kleine
*/
public class WellKnownCoreResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private SettableFuture<Set<String>> serviceDiscoveryFuture;

    public WellKnownCoreResponseProcessor(){
        serviceDiscoveryFuture = SettableFuture.create();
    }

    public SettableFuture<Set<String>> getServiceDiscoveryFuture(){
        return this.serviceDiscoveryFuture;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        Set<String> services = processWellKnownCoreResource(coapResponse);
        if(services != null){
            log.info("/.well-known/core resource succesfully processed, found {} services.", services.size());
            serviceDiscoveryFuture.set(services);
        }
        else{
            serviceDiscoveryFuture.setException(new WellKnownCoreResourceInvalidException());
        }
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        serviceDiscoveryFuture.setException(new ResourceDiscoveringTimeoutException(timeoutMessage.getRemoteAddress()));
    }

    private Set<String> processWellKnownCoreResource(CoapResponse coapResponse){

        ChannelBuffer payload = coapResponse.getPayload();

        if(payload.readableBytes() == 0)
            return null;

        Set<String> result = new TreeSet<String>();

        log.debug("Process ./well-known/core resource {}", payload.toString(Charset.forName("UTF-8")));

        //add links to the result set
        String[] links = payload.toString(Charset.forName("UTF-8")).split(",");

        for (String link : links){
            log.debug("Found service: " + link);
            //Ensure a "/" at the beginning of the path
            String path = link.substring(link.indexOf("<") + 1, link.indexOf(">"));
            if (path.indexOf("/") > 0)
                path = "/" + path;

            result.add(path);
        }
        return result;
    }
}
