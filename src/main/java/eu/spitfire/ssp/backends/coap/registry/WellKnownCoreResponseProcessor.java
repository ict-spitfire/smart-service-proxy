package eu.spitfire.ssp.backends.coap.registry;

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
import java.util.concurrent.ExecutorService;

/**
 * Instance of {@link CoapResponseProcessor} to process incoming responses from <code>.well-known/core</code> CoAP resources.
 *
 * @author Oliver Kleine
*/
public class WellKnownCoreResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private SettableFuture<Set<String>> wellKnownCoreFuture;
    private ExecutorService executorService;

    public WellKnownCoreResponseProcessor(SettableFuture<Set<String>> wellKnownCoreFuture,
                                          ExecutorService executorService){
        this.wellKnownCoreFuture = wellKnownCoreFuture;
        this.executorService = executorService;
    }

    @Override
    public void processCoapResponse(final CoapResponse coapResponse) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Set<String> services = processWellKnownCoreResponse(coapResponse);
                if(services != null){
                    log.info("/.well-known/core service succesfully read. Found {} services.", services.size());
                    wellKnownCoreFuture.set(services);
                }
                else{
                    wellKnownCoreFuture.setException(new WellKnownCoreInvalidException());
                }
            }
        });
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        wellKnownCoreFuture.setException(new WellKnownCoreTimeoutException(timeoutMessage.getRemoteAddress()));
    }


    private Set<String> processWellKnownCoreResponse(CoapResponse coapResponse){
        ChannelBuffer payload = coapResponse.getPayload();
        log.debug("Process ./well-known/core resource {}", payload.toString(Charset.forName("UTF-8")));

        //Check if there is content at all
        if(payload.readableBytes() == 0)
            return null;

        //Process the response from the .well-known/core service
        Set<String> result = new TreeSet<>();

        //add links to the result set
        String[] paths = payload.toString(Charset.forName("UTF-8")).split(",");

        for (String path : paths){
            log.debug("Found service: " + path);
            //Ensure a "/" at the beginning of the path
            String servicePath = path.substring(path.indexOf("<") + 1, path.indexOf(">"));
            if (servicePath.indexOf("/") > 0)
                servicePath = "/" + servicePath;

            result.add(servicePath);
        }
        return result;
    }
}
