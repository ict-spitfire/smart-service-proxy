package eu.spitfire.ssp.backends.files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.backends.generic.BackendResourceManager;
import eu.spitfire.ssp.backends.generic.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 17:19
 * To change this template use File | Settings | File Templates.
 */
public class FilesObserver extends DataOriginObserver {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private FilesRegistry filesRegistry;
    private BackendResourceManager<Path> backendResourceManager;

    public FilesObserver(FilesBackendComponentFactory backendComponentFactory) {
        super(backendComponentFactory);
        this.backendResourceManager = backendComponentFactory.getBackendResourceManager();
        this.filesRegistry = (FilesRegistry) backendComponentFactory.getDataOriginRegistry();
    }


    public void handleFileModification(Path file){
        log.info("File modified: {}", file);
        try{
            //Read models (i.e. resource states) from the file
            Model modelFromFile = FilesResourceToolBox.readModelFromFile(file);
            Map<URI, Model> modelsFromFile = FilesResourceToolBox.getModelsPerSubject(modelFromFile);

            List<URI> registeredResourceUris =
                    Arrays.asList(backendResourceManager.getResources(file).toArray(new URI[0]));

            List<URI> resourceUrisFromFile = Arrays.asList(modelsFromFile.keySet().toArray(new URI[0]));

            //Check if there were resources deleted from the file
            for(URI registeredResourceUri : registeredResourceUris){
                if(!resourceUrisFromFile.contains(registeredResourceUri)){
                    deleteResource(registeredResourceUri);
                }
            }

            //Check if there are new resources contained in the file
            for(URI resourceUriFromFile : resourceUrisFromFile){
                if(!registeredResourceUris.contains(resourceUriFromFile)){

                    log.info("Register new resource {}", resourceUriFromFile);
                    filesRegistry.registerResource(modelsFromFile.get(resourceUriFromFile), file);

                    log.info("Remove resource {} from model.", resourceUriFromFile);
                    modelFromFile.remove(modelsFromFile.get(resourceUriFromFile).listStatements());
                }
            }

            cacheResourcesStates(modelFromFile);
        }
        catch (Exception e){
            log.error("Error while processing modifications of file {}", file, e);
        }
    }

    public void handleFileDeletion(Path file){
        log.info("File deleted: {}", file);
        List<URI> registeredResourceUris =
                Arrays.asList(backendResourceManager.getResources(file).toArray(new URI[0]));
        for(URI resourceUri : registeredResourceUris){
            deleteResource(resourceUri);
        }
    }
}
