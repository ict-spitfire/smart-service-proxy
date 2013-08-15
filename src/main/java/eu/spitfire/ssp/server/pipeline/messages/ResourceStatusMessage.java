package eu.spitfire.ssp.server.pipeline.messages;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer;
import eu.spitfire.ssp.proxyservicemanagement.ProxyServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Date;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * Instances of {@link ResourceStatusMessage} contain the {@link URI} to identify the resource, a {@link Model}
 * containing the status of the resource and a {@link Date} to define the expiry of the actual status.
 *
 * @author Oliver Kleine
 */
public class ResourceStatusMessage {

    private static Logger log = LoggerFactory.getLogger(ResourceStatusMessage.class.getName());
    private URI resourceUri;
    private final Model resourceStatus;
    private final Date expiry;

    public ResourceStatusMessage(URI resourceUri, Model resourceStatus, Date expiry){
        this.resourceUri = resourceUri;

        this.resourceStatus = resourceStatus;
        this.expiry = expiry;
    }

    /**
     * Returns the {@link Model} containing the actual status of the resource
     * @return the {@link Model} containing the actual status of the resource
     */
    public Model getResourceStatus() {
        return resourceStatus;
    }

    /**
     * Returns the expiry of the actual status
     * @return the expiry of the actual status
     */
    public Date getExpiry() {
        return expiry;
    }

    /**
     * Returns the {@link URI} identifying the resource
     * @return the {@link URI} identifying the resource
     */
    public URI getResourceUri() {
        return resourceUri;
    }

    @Override
    public String toString(){
        return "[Resource status message] " + getResourceUri() + " (URI), " + getExpiry() + " (expiry)";
    }

    public static ResourceStatusMessage create(CoapResponse coapResponse, URI resourceUri) throws Exception{

        Model resourceStatus = ModelFactory.createDefaultModel();;

        //read payload from CoAP response
        byte[] coapPayload = new byte[coapResponse.getPayload().readableBytes()];
        coapResponse.getPayload().getBytes(0, coapPayload);

        if(coapResponse.getContentType() == OptionRegistry.MediaType.APP_SHDT){
            log.debug("SHDT payload in CoAP response.");
            (new ShdtDeserializer(64)).read_buffer(resourceStatus, coapPayload);
        }
        else{
            Language language = Language.getByCoapMediaType(coapResponse.getContentType());
            if(language == null){
                throw new ProxyServiceException(INTERNAL_SERVER_ERROR, "CoAP response had no semantic content type");
            }

            try{
                resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.lang);
            }
            catch(Exception e){
                log.error("Error while reading resource status from CoAP response!", e);
                throw new ProxyServiceException(INTERNAL_SERVER_ERROR,
                        "Error while reading resource status from CoAP response!", e);
            }
        }

        //Get expiry of resource
        Long maxAge = (Long) coapResponse.getOption(OptionRegistry.OptionName.MAX_AGE)
                .get(0).getDecodedValue();

        log.debug("Max-Age option of CoAP response: {}", maxAge);

        return new ResourceStatusMessage(resourceUri, resourceStatus, getExpiryDate(maxAge));
    }

    private static Date getExpiryDate(Long secondsFromNow){
        return new Date(System.currentTimeMillis() + 1000 * secondsFromNow);
    }
}
