package eu.spitfire.ssp.gateways.coap;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.gateways.ProxyServiceException;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Date;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * This is a helper class to provide abstract methods to get proxy relevant information from CoAP responses.
 *
 * @author Oliver Kleine
 */
public abstract class CoapProxyTools {

    private static Logger log = LoggerFactory.getLogger(CoapProxyTools.class.getName());

    /**
     * Reads the payload of the given {@link CoapResponse} into an instance of {@link Model} and returns that
     * {@link Model}.
     *
     * @param coapResponse the {@link CoapResponse} to read the payload
     * @param resourceUri the {@link URI} of the resource, i.e. the service that was requested
     * @return a {@link Model} containing the information from the payload
     *
     * @throws ProxyServiceException if an error occurred
     */
    public static Model getModelFromCoapResponse(CoapResponse coapResponse, URI resourceUri)
            throws ProxyServiceException{

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
                throw new ProxyServiceException(resourceUri, INTERNAL_SERVER_ERROR,
                        "CoAP response had no semantic content type");
            }

            try{
                resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.lang);
            }
            catch(Exception e){
                log.error("Error while reading resource status from CoAP response!", e);
                throw new ProxyServiceException(resourceUri, INTERNAL_SERVER_ERROR,
                        "Error while reading resource status from CoAP response!", e);
            }
        }

        return resourceStatus;
    }

    /**
     * Converts the max-age option from the given {@link CoapResponse} into a {@link Date}.
     *
     * @param coapResponse the {@link CoapResponse} to take its max-age option
     *
     * @return the {@link Date} the actual status of the resource expires accoording to the max-age option
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
