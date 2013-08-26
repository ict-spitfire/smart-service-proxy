package eu.spitfire.ssp.gateways.files;

import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.server.payloadserialization.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper class to handle semantic information from files
 *
 * @author Oliver Kleine
 */
public abstract class FilesProxyTools {

    private static Logger log = LoggerFactory.getLogger(FilesProxyTools.class.getName());

    /**
     * Reads the file at the given path and returns a {@link Map} containing a {@link Model} instance for each
     * subject that was found in the given file. The keys of this Map are the {@link URI}s identifying the subjects.
     *
     * @param filePath the {@link Path} of the local file containing the semantic informations
     *
     * @return a {@link Map} containing the subjects in the given file as keys and a {@link Model} for each such
     * subject.
     */
    public static Map<URI, Model> readModelsFromFile(Path filePath){
        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(filePath.toString()));
            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            return getModelsPerSubject(model);

        }
        catch (Exception e){
            log.error("Something is wrong with file: {}", filePath, e);
            return new HashMap<>();
        }
    }

    /**
     * Splits the given {@link Model} into several {@link Model} instances, one for each subject contained in the
     * given model.
     *
     * @param model a {@link Model} instance to be split up into models per subject
     *
     * @return a {@link Map} containing the subjects of the given model as keys and the appropriate model as value
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
