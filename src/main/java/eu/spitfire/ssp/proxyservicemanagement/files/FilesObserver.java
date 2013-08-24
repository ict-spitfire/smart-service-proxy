package eu.spitfire.ssp.proxyservicemanagement.files;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.proxyservicemanagement.AbstractResourceObserver;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 24.08.13
 * Time: 00:47
 * To change this template use File | Settings | File Templates.
 */
public class FilesObserver extends AbstractResourceObserver implements Runnable{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;
    private Multimap<Path, URI> observedResources;
    private FilesProxyServiceManager serviceManager;
    private Path observedDirectory;

    public FilesObserver(FilesProxyServiceManager serviceManager, Path directory,
                         ScheduledExecutorService scheduledExecutorService,
                         LocalServerChannel localChannel) throws IOException {

        super(scheduledExecutorService, localChannel);

        this.observedDirectory = directory;

        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKeys = new HashMap<>();

        this.observedResources = Multimaps.synchronizedMultimap(HashMultimap.<Path, URI>create());
        this.serviceManager = serviceManager;

        observeDirectoryRecursively(directory);
    }

    /**
     * Returns a {@link Collection} containing all the observed files (incl. directories)
     * @return a {@link Collection} containing all the observed files (incl. directories)
     */
    public Collection<Path> getObservedDirectories(){
        return watchKeys.values();
    }

    public Collection<Path> getObservedFiles(){
        return this.observedResources.keySet();
    }

    public Collection<URI> getObservedResources(Path file){
        return this.observedResources.get(file);
    }

    public Path getObservedDirectory(){
        return this.observedDirectory;
    }

    private void observeDirectoryRecursively(Path directoryPath) throws IOException{
        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException{

                WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                watchKeys.put(watchKey, dir);

                log.info("Added directory to be observed: {}", dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Map<URI, Model> getModelsPerSubject(Model completeModel){

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

    private void handleFileModification(Path filePath){

    }

    private void handleFileCreation(final Path filePath){
        try {
            //If a new directory was created then try to add it
            if (Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
                observeDirectoryRecursively(filePath);
                return;
            }

            //If a file was created then register it as new resource
            if(filePath.toString().endsWith(".n3")){

                Model completeModel = ModelFactory.createDefaultModel();
                FileInputStream inputStream = new FileInputStream(filePath.toFile());
                completeModel.read(inputStream, null, Language.RDF_N3.lang);

                final Map<URI, Model> resources = getModelsPerSubject(completeModel);

                for(final URI resourceUri : resources.keySet()){
                    final SettableFuture<URI> resourceRegistrationFuture = SettableFuture.create();
                    resourceRegistrationFuture.addListener(new Runnable(){
                        @Override
                        public void run() {
                            try{
                                URI resourceProxyUri = resourceRegistrationFuture.get();
                                observedResources.put(filePath, resourceUri);
                                log.info("Successfully registered resource {} from file {}.", resourceUri, filePath);

                                //Send status to cache (expires in ~1 year)
                                ResourceStatusMessage resourceStatusMessage =
                                        new ResourceStatusMessage(resourceUri, resources.get(resourceUri),
                                                new Date(System.currentTimeMillis() + 31560000000L));

                                updateResourceStatus(resourceStatusMessage);
                            }
                            catch (Exception e) {
                                log.error("Exception while creating service to observe a file.", e);
                            }
                        }
                    }, getScheduledExecutorService());
                    serviceManager.registerResource(resourceRegistrationFuture, resourceUri);
                }
            }
        }
        catch (IOException e) {
            log.error("Exception while adding new directory to be observed.", e);
        }
    }

    @Override
    public void run() {
        while(true){
            WatchKey watchKey;
            try{
                watchKey = watchService.take();
            }
            catch (InterruptedException e) {
                log.error("Exception while taking watch key.", e);
                return;
            }

            Path directory = watchKeys.get(watchKey);
            if(directory == null){
                log.error("Directory not recognized ({}).", directory);
                continue;
            }

            for(WatchEvent ev : watchKey.pollEvents()){
                //Which kind of event occured?
                WatchEvent.Kind eventKind = ev.kind();

                if(eventKind == OVERFLOW){
                    log.warn("Unhandled event kind OVERFLOW");
                    continue;
                }

                Object context = ev.context();
                if(context instanceof Path){
                    Path filePath = directory.resolve((Path) context);
                    log.debug("Event {} at path {}", eventKind, filePath);

                    if(eventKind == ENTRY_CREATE){
                        handleFileCreation(filePath);
                    }
                    else if(eventKind == ENTRY_MODIFY){
                        handleFileModification(filePath);
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = watchKey.reset();
            if (!valid) {
                log.error("Stopped observation of directory {}.", watchKeys.get(watchKey));
                watchKeys.remove(watchKey);
            }
        }
    }


}

