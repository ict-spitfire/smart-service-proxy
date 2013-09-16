package eu.spitfire.ssp.backends.coap;

import com.hp.hpl.jena.rdf.model.*;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.backends.ProxyServiceException;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.09.13
 * Time: 16:27
 * To change this template use File | Settings | File Templates.
 */
public abstract class ResourceToolBox {

    private static Logger log = LoggerFactory.getLogger(ResourceToolBox.class.getName());

    public static Model getModelFromHttpMessage(HttpMessage httpMessage) throws ProxyServiceException{

        Model resourceStatus = ModelFactory.createDefaultModel();

        //read payload from HTTP message
        byte[] httpPayload = new byte[httpMessage.getContent().readableBytes()];

        httpMessage.getContent().getBytes(0, httpPayload);

        Language language = Language.getByHttpMimeType(httpMessage.getHeader(HttpHeaders.Names.CONTENT_TYPE));
        if(language == null){
            throw new ProxyServiceException(null, INTERNAL_SERVER_ERROR, "Could not process content type: " +
                    HttpHeaders.Names.CONTENT_TYPE + ": " + httpMessage.getHeader(HttpHeaders.Names.CONTENT_TYPE));
        }

        try{
            resourceStatus.read(new ByteArrayInputStream(httpPayload), null, language.lang);
        }
        catch(Exception e){
            log.error("Error while reading resource status from CoAP response!", e);
            throw new ProxyServiceException(null, INTERNAL_SERVER_ERROR,
                    "Error while reading resource status from HTTP message paylaod!", e);
        }

        return resourceStatus;
    }

    /**
     * Reads the payload of the given {@link de.uniluebeck.itm.ncoap.message.CoapResponse} into an instance of {@link Model} and returns that
     * {@link Model}.
     *
     * @param coapResponse the {@link de.uniluebeck.itm.ncoap.message.CoapResponse} to read the payload
     * @return a {@link Model} containing the information from the payload
     *
     * @throws eu.spitfire.ssp.backends.ProxyServiceException if an error occurred
     */
    public static Model getModelFromCoapResponse(CoapResponse coapResponse)
            throws ProxyServiceException {

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
                throw new ProxyServiceException(null, INTERNAL_SERVER_ERROR,
                        "CoAP response had no semantic content type");
            }

            try{
                resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.lang);
            }
            catch(Exception e){
                log.error("Error while reading resource status from CoAP response!", e);
                throw new ProxyServiceException(null, INTERNAL_SERVER_ERROR,
                        "Error while reading resource status from CoAP response!", e);
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

    public static Model readModelFromFile(Path filePath) throws FileNotFoundException {
        BufferedReader fileReader = new BufferedReader(new FileReader(filePath.toString()));
        Model model = ModelFactory.createDefaultModel();
        model.read(fileReader, null, Language.RDF_N3.lang);

        return model;
    }

    /**
     * Reads the file at the given path and returns a {@link Map} containing a {@link Model} instance for each
     * subject that was found in the given file. The keys of this Map are the {@link URI}s identifying the subjects.
     *
     * @param filePath the {@link java.nio.file.Path} of the local file containing the semantic informations
     *
     * @return a {@link Map} containing the subjects in the given file as keys and a {@link Model} for each such
     * subject.
     */
    public static Map<URI, Model> readResourcesFromFile(Path filePath) {
        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(filePath.toString()));
            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            return getModelsPerSubject(model);
        }
        catch (Exception e) {
            log.error("Exception while reading resources from file {}", filePath, e);
            return new HashMap<>();
        }
    }

    /**
     * Splits the given {@link com.hp.hpl.jena.rdf.model.Model} into several {@link com.hp.hpl.jena.rdf.model.Model} instances, one for each subject contained in the
     * given model.
     *
     * @param model a {@link com.hp.hpl.jena.rdf.model.Model} instance to be split up into models per subject
     *
     * @return a {@link java.util.Map} containing the subjects of the given model as keys and the appropriate model as value
     */
    public static Map<URI, Model> getModelsPerSubject(Model model){

        Map<URI, Model> result = new HashMap<>();

        try{
            //Iterate over all subjects in the Model
            ResIterator subjectIterator = model.listSubjects();
            while(subjectIterator.hasNext()){
                Resource resource = subjectIterator.next();

                log.debug("Create model for subject {}.", resource.getURI());
                Model subModel = ModelFactory.createDefaultModel();

                //Iterate over all properties of the actual subject
                StmtIterator stmtIterator = resource.listProperties();
                while(stmtIterator.hasNext()){
                    subModel = subModel.add(stmtIterator.next());
                }

                result.put(new URI(resource.getURI()), subModel);
            }
        }
        catch (URISyntaxException e) {
            log.error("Exception while creating sub-models.", e);
        }

        return result;
    }
}
