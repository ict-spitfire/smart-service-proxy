package eu.spitfire.ssp.backend.coap;

import de.uzl.itm.ncoap.message.CoapMessage;
import de.uzl.itm.ncoap.message.CoapResponse;
import eu.spitfire.ssp.server.internal.utils.Language;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

/**
 * Provides static tools to process CoAP message
 *
 * @author Oliver Kleine
 */
public abstract class CoapTools {

    private static Logger log = LoggerFactory.getLogger(CoapTools.class.getName());

    /**
     * Reads the content of the given {@link de.uzl.itm.ncoap.message.CoapResponse} and deserializes that content
     * into a {@link org.apache.jena.rdf.model.Model} according to the
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#CONTENT_FORMAT}.
     *
     * @param coapResponse the {@link de.uzl.itm.ncoap.message.CoapResponse} to read the content from.
     *
     * @return a {@link org.apache.jena.rdf.model.Model} that contains the triples from the given
     * {@link de.uzl.itm.ncoap.message.CoapResponse}s content
     */
    public static Model getModelFromCoapResponse(CoapResponse coapResponse){

        try{
            Model resourceStatus = ModelFactory.createDefaultModel();

            //read payload from CoAP response
            byte[] coapPayload = new byte[coapResponse.getContent().readableBytes()];
            coapResponse.getContent().getBytes(0, coapPayload);

            Language language = Language.getByCoapContentFormat(coapResponse.getContentFormat());

            if(language == null)
                return null;

            log.debug("RDF Payload:\n{}", new String(coapPayload, CoapMessage.CHARSET));

            resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.getRdfFormat().getLang().getName());
            return resourceStatus;
        }

        catch(Exception ex){
            log.error("Could not read content from CoAP response!", ex);
            return null;
        }
    }
}
