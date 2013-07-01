package eu.spitfire.ssp.gateways.coap.noderegistration;

import com.google.common.util.concurrent.ListenableFuture;
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
* Created with IntelliJ IDEA.
* User: olli
* Date: 12.04.13
* Time: 15:41
* To change this template use File | Settings | File Templates.
*/
//Handles the registration process for new nodes in a new thread
public class WellKnownCoreProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private SettableFuture<Set<String>> discoveringFuture = SettableFuture.create();

    public ListenableFuture<Set<String>> getDiscoveringFuture() {
        return discoveringFuture;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        Set<String> services = processWellKnownCoreResource(coapResponse);
        if(services != null){
            log.info("/.well-known/core resource succesfully processed, found {} services.", services.size());
            discoveringFuture.set(services);
        }
        else
            discoveringFuture.setException(new ResourceInvalidException());
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        discoveringFuture.setException(new ResourceDiscoveringTimeoutException(timeoutMessage.getRemoteAddress()));
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
