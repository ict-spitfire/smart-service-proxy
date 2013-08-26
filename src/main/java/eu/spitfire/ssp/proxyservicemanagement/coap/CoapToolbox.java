package eu.spitfire.ssp.proxyservicemanagement.coap;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.proxyservicemanagement.ProxyServiceException;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Date;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 26.08.13
 * Time: 13:33
 * To change this template use File | Settings | File Templates.
 */
public abstract class CoapToolbox {

    private static Logger log = LoggerFactory.getLogger(CoapToolbox.class.getName());

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

    public static Date getExpiryFromCoapResponse(CoapResponse coapResponse){

        //Get expiry of resource
        Long maxAge = (Long) coapResponse.getOption(OptionRegistry.OptionName.MAX_AGE)
                .get(0)
                .getDecodedValue();

        log.debug("Max-Age option of CoAP response: {}", maxAge);

        return new Date(System.currentTimeMillis() + 1000 * maxAge);
    }
}
