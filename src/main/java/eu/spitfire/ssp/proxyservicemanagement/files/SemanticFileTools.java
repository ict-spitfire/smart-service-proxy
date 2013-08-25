package eu.spitfire.ssp.proxyservicemanagement.files;

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
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 25.08.13
 * Time: 15:21
 * To change this template use File | Settings | File Templates.
 */
public abstract class SemanticFileTools {

    private static Logger log = LoggerFactory.getLogger(SemanticFileTools.class.getName());

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

    public static Map<URI, Model> getModelsPerSubject(Model completeModel){

        Map<URI, Model> result = new HashMap<>();

        try{
            //Iterate over all subjects in the Model
            ResIterator subjectIterator = completeModel.listSubjects();
            while(subjectIterator.hasNext()){
                Resource resource = subjectIterator.next();

                log.debug("Create model for subject {}.", resource.getURI());
                Model model = ModelFactory.createDefaultModel();

                //Iterate over all properties of the actual subject
                StmtIterator stmtIterator = resource.listProperties();
                while(stmtIterator.hasNext()){
                    model = model.add(stmtIterator.next());
                }

                result.put(new URI(resource.getURI()), model);
            }
        }
        catch (URISyntaxException e) {
            log.error("Exception while creating sub-models.", e);
        }

        return result;
    }
}
