package eu.spitfire.ssp.backends.files_old;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.registration.DataOriginManager;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;

/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 04.10.13
* Time: 17:19
* To change this template use File | Settings | File Templates.
*/
public class OldFilesObserver extends DataOriginObserver<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private OldFilesRegistry oldFilesRegistry;
    private DataOriginManager<Path> dataOriginManager;

    public OldFilesObserver(OldFilesBackendComponentFactory componentFactory) {
        super(componentFactory.getLocalChannel(), componentFactory.getExecutorService());

        this.dataOriginManager = componentFactory.getDataOriginManager();
        this.oldFilesRegistry = (OldFilesRegistry) componentFactory.getDataOriginRegistry();
    }


    public void handleFileModification(Path file){
        log.info("File modified: {}", file);
        try{
            //Read models (i.e. resource states) from the file
            Model modelFromFile = OldFilesResourceToolBox.readModelFromFile(file);
////            Map<URI, Model> modelsFromFile = OldFilesResourceToolBox.getModelsPerSubject(modelFromFile);
//
//            List<URI> registeredResourceUris =
//                    Arrays.asList(dataOriginManager.getResources(file).toArray(new URI[0]));
//
//            List<URI> resourceUrisFromFile = Arrays.asList(modelsFromFile.keySet().toArray(new URI[0]));
//
//            //Check if there were resources deleted from the file
//            for(URI registeredResourceUri : registeredResourceUris){
//                if(!resourceUrisFromFile.contains(registeredResourceUri)){
//                    deleteResource(registeredResourceUri);
//                }
//            }
//
//            //Check if there are new resources contained in the file
//            for(URI resourceUriFromFile : resourceUrisFromFile){
//                if(!registeredResourceUris.contains(resourceUriFromFile)){
//
//                    log.info("Register new resource {}", resourceUriFromFile);
//                    oldFilesRegistry.registerResource(modelsFromFile.get(resourceUriFromFile), file);
//
//                    log.info("Remove resource {} from model.", resourceUriFromFile);
//                    modelFromFile.remove(modelsFromFile.get(resourceUriFromFile).listStatements());
//                }
//            }

            cacheResourcesStates(modelFromFile);
        }
        catch (Exception e){
            log.error("Error while processing modifications of file {}", file, e);
        }
    }

    public void handleFileDeletion(Path file){
        log.info("File deleted: {}", file);
        List<URI> registeredResourceUris =
                Arrays.asList(dataOriginManager.getResources(file).toArray(new URI[0]));
        for(URI resourceUri : registeredResourceUris){
            deleteResource(resourceUri);
        }
    }

    @Override
    public void startObservation(DataOrigin<Path> dataOrigin) {

    }

    @Override
    public WrappedDataOriginStatus getStatus(DataOrigin<Path> dataOrigin) {
        return null;
    }
}
