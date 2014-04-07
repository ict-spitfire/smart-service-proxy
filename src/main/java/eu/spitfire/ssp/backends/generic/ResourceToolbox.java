//package eu.spitfire.ssp.backends.generic;
//
//import com.hp.hpl.jena.rdf.model.*;
//import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
//import eu.spitfire.ssp.utils.Language;
//import org.jboss.netty.handler.codec.http.HttpHeaders;
//import org.jboss.netty.handler.codec.http.HttpMessage;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.*;
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.util.HashMap;
//import java.util.Map;
//
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
//
///**
// * Created with IntelliJ IDEA.
// * User: olli
// * Date: 03.09.13
// * Time: 16:27
// * To change this template use File | Settings | File Templates.
// */
//public abstract class ResourceToolbox {
//
//    private static Logger log = LoggerFactory.getLogger(ResourceToolbox.class.getName());
//
//    public static Model getModelFromHttpMessage(HttpMessage httpMessage) throws SemanticResourceException {
//
//        Model model = ModelFactory.createDefaultModel();
//
//        //read payload from HTTP message
//        byte[] httpPayload = new byte[httpMessage.getContent().readableBytes()];
//
//        httpMessage.getContent().getBytes(0, httpPayload);
//
//        Language language = Language.getByHttpMimeType(httpMessage.getHeader(HttpHeaders.Names.CONTENT_TYPE));
//        if(language == null){
//            throw new SemanticResourceException(null, INTERNAL_SERVER_ERROR, "Could not process content type: " +
//                    HttpHeaders.Names.CONTENT_TYPE + ": " + httpMessage.getHeader(HttpHeaders.Names.CONTENT_TYPE));
//        }
//
//        try{
//            model.read(new ByteArrayInputStream(httpPayload), null, language.lang);
//        }
//        catch(Exception e){
//            log.error("Error while reading resource status from CoAP response!", e);
//            throw new SemanticResourceException(null, INTERNAL_SERVER_ERROR,
//                    "Error while reading resource status from HTTP message paylaod!", e);
//        }
//
//        return model;
//    }
//
//    /**
//     * Splits the given {@link com.hp.hpl.jena.rdf.model.Model} into several {@link com.hp.hpl.jena.rdf.model.Model} instances, one for each subject contained in the
//     * given model.
//     *
//     * @param model a {@link com.hp.hpl.jena.rdf.model.Model} instance to be split up into models per subject
//     *
//     * @return a {@link java.util.Map} containing the subjects of the given model as keys and the appropriate model as value
//     */
//    public static Map<URI, Model> getModelsPerSubject(Model model){
//        try{
//            Map<URI, Model> result = new HashMap<>();
//
//            //Iterate over all subjects in the Model
//            ResIterator subjectIterator = model.listSubjects();
//            while(subjectIterator.hasNext()){
//                Resource resource = subjectIterator.next();
//
//                Model subModel = ModelFactory.createDefaultModel();
//
//                //Iterate over all properties of the actual subject
//                StmtIterator stmtIterator = resource.listProperties();
//                while(stmtIterator.hasNext()){
//                    subModel = subModel.add(stmtIterator.next());
//                }
//
//                result.put(new URI(resource.getURI()), subModel);
//            }
//
//            return result;
//        }
//        catch(URISyntaxException e){
//            log.error("Malformed URI!", e);
//            return new HashMap<>(0);
//        }
//    }
//
////    /**
////     * Reads the file at the given path and returns a {@link Map} containing a {@link Model} instance for each
////     * subject that was found in the given file. The keys of this Map are the {@link URI}s identifying the subjects.
////     *
////     * @param filePath the {@link java.nio.file.Path} of the local file containing the semantic informations
////     *
////     * @return a {@link Map} containing the subjects in the given file as keys and a {@link Model} for each such
////     * subject.
////     */
////    public static Map<URI, Model> readResourcesFromFile(Path filePath) {
////        try{
////            BufferedReader fileReader = new BufferedReader(new FileReader(filePath.toString()));
////            Model model = ModelFactory.createDefaultModel();
////            model.read(fileReader, null, Language.RDF_N3.lang);
////
////            return getModelsPerSubject(model);
////        }
////        catch (Exception e) {
////            log.error("Exception while reading resources from file {}", filePath, e);
////            return new HashMap<>();
////        }
////    }
//
//
//}
