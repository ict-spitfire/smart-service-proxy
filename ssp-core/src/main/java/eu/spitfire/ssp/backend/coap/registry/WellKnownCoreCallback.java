package eu.spitfire.ssp.backend.coap.registry;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SettableFuture;
import de.uzl.itm.ncoap.application.client.linkformat.LinkFormatDecoder;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.EmptyLinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.LinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.LongLinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.StringLinkAttribute;
import de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback;
import de.uzl.itm.ncoap.message.CoapMessage;
import de.uzl.itm.ncoap.message.CoapResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instance of {@link de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback} to process incoming responses
 * from <code>.well-known/core</code> CoAP resources.
 *
 * @author Oliver Kleine
 */
public class WellKnownCoreCallback extends ClientCallback{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private SettableFuture<Map<String, Set<LinkAttribute>>> wellKnownCoreFuture;
    private AtomicInteger transmissionCounter;

    public WellKnownCoreCallback(){
        this.transmissionCounter = new AtomicInteger(0);
        this.wellKnownCoreFuture = SettableFuture.create();
    }

    /**
     * Returns the {@link com.google.common.util.concurrent.SettableFuture} that is set with a
     * {@link com.google.common.collect.Multimap} with the paths to the detected CoAP Web Services as keys and their
     * {@link de.uzl.itm.ncoap.application.server.webresource.linkformat.LinkAttribute}s as values.
     *
     * @return the {@link com.google.common.util.concurrent.SettableFuture} that is set with a
     * {@link com.google.common.collect.Multimap} with the paths to the detected CoAP Web Services as keys and their
     * {@link de.uzl.itm.ncoap.application.server.webresource.linkformat.LinkAttribute}s as values.
     */
    public SettableFuture<Map<String, Set<LinkAttribute>>> getWellKnownCoreFuture(){
        return this.wellKnownCoreFuture;
    }

    /**
     * Sets the {@link com.google.common.util.concurrent.SettableFuture} returned by {@link #getWellKnownCoreFuture()}
     * according to the content of the given {@link de.uzl.itm.ncoap.message.CoapResponse} which is supposed
     * to be in {@link de.uzl.itm.ncoap.message.options.ContentFormat#APP_LINK_FORMAT}.
     *
     * @param coapResponse the {@link de.uzl.itm.ncoap.message.CoapResponse} which contains some content in
     *                     {@link de.uzl.itm.ncoap.message.options.ContentFormat#APP_LINK_FORMAT}
     */
    @Override
    public void processCoapResponse(final CoapResponse coapResponse) {
        try {
            ChannelBuffer payload = coapResponse.getContent();
            wellKnownCoreFuture.set(LinkFormatDecoder.decode(payload.toString(CoapMessage.CHARSET)));
        }
        catch (Exception ex) {
            log.error("Could not process .well-known/core resource!", ex);
            wellKnownCoreFuture.setException(ex);
        }
    }


    private Collection<LinkAttribute> deserializeLinkAttributes(String attribute) throws IllegalArgumentException{

        String[] keyAndValues = attribute.split("=");
        String key = keyAndValues[0];

        int attributeType = LinkAttribute.getAttributeType(key);
        Collection<LinkAttribute> result = new ArrayList<>();

        if(attributeType == LinkAttribute.EMPTY_ATTRIBUTE){
            result.add(new EmptyLinkAttribute(key));
            return result;
        }

        if(keyAndValues.length != 2)
            throw new IllegalArgumentException("No value for non-empty link attribute found: " + attribute);

        String[] values = keyAndValues[1].split(" ");

        if(attributeType == LinkAttribute.LONG_ATTRIBUTE){
            for(String value : values)
                result.add(new LongLinkAttribute(key, Long.parseLong(value)));
        }

        else if(attributeType == LinkAttribute.STRING_ATTRIBUTE){
            for(String value : values)
                result.add(new StringLinkAttribute(key, value));
        }

        return result;
    }


    @Override
    public void processTransmissionTimeout() {
        String message = "Transmission of request for .well-known/core timed out!";
        log.error(message);
        wellKnownCoreFuture.setException(new TimeoutException(message));
    }


    @Override
    public void processRetransmission(){
        int count = this.transmissionCounter.incrementAndGet();
        log.warn("Transmit #{} of request for .well-known/core completed.", count);
    }
}
