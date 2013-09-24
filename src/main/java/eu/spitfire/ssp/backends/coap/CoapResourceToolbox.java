package eu.spitfire.ssp.backends.coap;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.backends.utils.InvalidSemanticContentException;
import eu.spitfire.ssp.backends.utils.ResourceToolbox;
import eu.spitfire.ssp.backends.utils.UnsupportedMediaTypeException;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Date;

/**
 * This is a helper class to provide abstract methods to get proxy relevant information from CoAP responses.
 *
 * @author Oliver Kleine
 */
public abstract class CoapResourceToolbox extends ResourceToolbox {

    private static Logger log = LoggerFactory.getLogger(CoapResourceToolbox.class.getName());

    /**
     * Reads the payload of the given {@link de.uniluebeck.itm.ncoap.message.CoapResponse} into an instance of {@link com.hp.hpl.jena.rdf.model.Model} and returns that
     * {@link com.hp.hpl.jena.rdf.model.Model}.
     *
     * @param coapResponse the {@link de.uniluebeck.itm.ncoap.message.CoapResponse} to read the payload
     * @return a {@link com.hp.hpl.jena.rdf.model.Model} containing the information from the payload
     *
     * @throws eu.spitfire.ssp.backends.utils.DataOriginException if an error occurred
     */
    public static Model getModelFromCoapResponse(CoapResponse coapResponse)
            throws UnsupportedMediaTypeException, InvalidSemanticContentException {

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
                throw new UnsupportedMediaTypeException(coapResponse.getContentType().toString());
            }

            try{
                resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.lang);
            }
            catch(Exception e){
                log.error("Error while reading resource status from CoAP response!", e);
                throw new InvalidSemanticContentException(e);
            }
        }

        return resourceStatus;
    }

    /**
     * Converts the max-age option from the given {@link CoapResponse} into a {@link java.util.Date}.
     *
     * @param coapResponse the {@link CoapResponse} to take its max-age option
     *
     * @return the {@link java.util.Date} the actual status of the resource expires accoording to the max-age option
     * of the given {@link CoapResponse}
     */
    public static Date getExpiryFromCoapResponse(CoapResponse coapResponse){

        //Get expiry of resource
        Long maxAge = (Long) coapResponse.getOption(OptionRegistry.OptionName.MAX_AGE)
                .get(0)
                .getDecodedValue();

        log.debug("Max-Age option of CoAP response: {}", maxAge);

        return new Date(System.currentTimeMillis() + 1000 * maxAge);
    }
}
