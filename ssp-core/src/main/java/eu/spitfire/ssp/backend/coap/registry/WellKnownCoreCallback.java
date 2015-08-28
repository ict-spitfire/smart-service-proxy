package eu.spitfire.ssp.backend.coap.registry;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SettableFuture;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.EmptyLinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.LinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.LongLinkAttribute;
import de.uzl.itm.ncoap.application.server.webresource.linkformat.StringLinkAttribute;
import de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback;
import de.uzl.itm.ncoap.message.CoapResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instance of {@link de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback} to process incoming responses
 * from <code>.well-known/core</code> CoAP resources.
 *
 * @author Oliver Kleine
 */
public class WellKnownCoreCallback extends ClientCallback{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private SettableFuture<Multimap<String, LinkAttribute>> wellKnownCoreFuture;
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
    public SettableFuture<Multimap<String, LinkAttribute>> getWellKnownCoreFuture(){
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
            Multimap<String, LinkAttribute> webservices = processWellKnownCoreResponse(coapResponse);
            wellKnownCoreFuture.set(webservices);
        }
        catch (Exception ex) {
            log.error("Could not process .well-known/core resource!", ex);
            wellKnownCoreFuture.setException(ex);
        }
    }


    private Multimap<String, LinkAttribute> processWellKnownCoreResponse(CoapResponse coapResponse){

        ChannelBuffer payload = coapResponse.getContent();
        log.debug("Process ./well-known/core resource {}", payload.toString(Charset.forName("UTF-8")));

        Multimap<String, LinkAttribute> result = HashMultimap.create();

        //Check if there is content at all
        if(payload.readableBytes() == 0)
            return result;

        //add links to the result set
        String[] webservices = payload.toString(Charset.forName("UTF-8")).split(",");

        for (String webservice : webservices){
            if(webservice.contains(".well-known/core")){
                continue;
            }

            String[] attributes = webservice.split(";");

            if(attributes[0].startsWith("\n")){
                attributes[0] = attributes[0].substring(1);
            }


            if(!(attributes[0].startsWith("</") && attributes[0].endsWith(">"))){
                log.error("Malformed webservice path in .well-known/core: {}", attributes[0]);
                continue;
            }

            String webservicePath = attributes[0].substring(2, attributes[0].length() - 1);
            log.info("Found webservice path in .well-known/core: {}", webservicePath);

            if(attributes.length == 1){
                result.put(webservicePath, null);
            }

            else{
                for(int i = 1; i < attributes.length; i++){
                    try{
                        result.putAll(webservicePath, deserializeLinkAttributes(attributes[i]));
                    }
                    catch(IllegalArgumentException ex){
                        log.warn("Could not de-serialize link attribute for webservice {}: {}!", webservicePath,
                                attributes[i]);
                    }
                }
            }
        }

        return result;
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
        log.info("Transmit #{} of request for .well-known/core resource of host {} completed.", count);
    }
}
