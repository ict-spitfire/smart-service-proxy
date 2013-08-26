package eu.spitfire.ssp.proxyservicemanagement.files;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.proxyservicemanagement.AbstractResourceObserver;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
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
    private FilesServiceManager serviceManager;
    private Path observedDirectory;

    public FilesObserver(FilesServiceManager serviceManager, Path directory,
                         ScheduledExecutorService scheduledExecutorService,
                         LocalServerChannel localChannel) throws IOException {

        super(scheduledExecutorService, localChannel);

        this.observedDirectory = directory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKeys = Collections.synchronizedMap(new HashMap<WatchKey, Path>());
        this.observedResources = Multimaps.synchronizedMultimap(HashMultimap.<Path, URI>create());
        this.serviceManager = serviceManager;

        observeDirectoryRecursively(directory);
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
                //Which kind of event occurred?
                WatchEvent.Kind eventKind = ev.kind();

                if(eventKind == OVERFLOW){
                    log.warn("Unhandled event kind OVERFLOW");
                    continue;
                }

                Object context = ev.context();
                if(context instanceof Path){
                    Path filePath = directory.resolve((Path) context);
                    log.debug("Event {} at path {}", eventKind, filePath);

                    if(eventKind == ENTRY_CREATE || eventKind == ENTRY_MODIFY){
                        handleFileCreationOrModification(filePath);
                    }
                    else if(eventKind == ENTRY_DELETE){
                        handleFileDeletion(filePath);
                    }
                }
            }

            // reset key and remove from set if directory is no longer accessible
            boolean valid = watchKey.reset();
            if (!valid) {
                log.error("Stopped observation of directory {}.", watchKeys.get(watchKey));
                watchKeys.remove(watchKey);
            }
        }
    }

    /**
     * The invocation of this method causes the given directory and all its subdirectories to be observed
     *
     * @param directoryPath the directory to be observed
     */
    private void observeDirectoryRecursively(Path directoryPath){
        try{
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
        } catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }

    private void handleFileCreationOrModification(final Path filePath){
        //If a new directory was created then try to add it
        if (Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
            observeDirectoryRecursively(filePath);
            return;
        }

        //If a file is no N3 file then ignore it
        if(!filePath.toString().endsWith(".n3")){
            log.warn("File is no N3 file {}. IGNORE!", filePath);
            return;
        }

        Map<URI, Model> resourcesFromFile = SemanticFileTools.readModelsFromFile(filePath);

        //Detect resources which where included in the previous version of this file but not in the new version
        List<URI> deletedResourceUris = new ArrayList<>();
        for(URI resourceUri : observedResources.get(filePath).toArray(new URI[0])){
            if(!resourcesFromFile.keySet().contains(resourceUri)){
                log.debug("Resource {} was deleted from file!", resourceUri);
                deletedResourceUris.add(resourceUri);
            }
        }

        //Delete resources which where included in the previous version of this file but not in the new version
        for(URI resourceUri : deletedResourceUris){
            removeResourceStatusFromCache(resourceUri);
            observedResources.remove(filePath, resourceUri);
        }

        //Register or update resources from file (expiry is in ~1 year)
        for(URI resourceUri : resourcesFromFile.keySet()){
            if(observedResources.values().contains(resourceUri)){
                cacheResourceStatus(resourceUri, resourcesFromFile.get(resourceUri));
            }
            else{
                registerResource(filePath, resourceUri, resourcesFromFile.get(resourceUri));
            }
        }
    }

    private void handleFileDeletion(Path filePath){
        //If a new directory was created then try to add it
        if (Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
            log.info("Nothing to do when a directory was deleted ({}).", filePath);
            return;
        }

        Collection<URI> deletedResources = observedResources.get(filePath);
        for(URI resourceUri : deletedResources){
            removeResourceStatusFromCache(resourceUri);
        }

        observedResources.removeAll(filePath);
    }

    private void registerResource(final Path filePath, final URI resourceUri, final Model model){
        final SettableFuture<URI> resourceRegistrationFuture = serviceManager.registerResource(resourceUri);
        resourceRegistrationFuture.addListener(new Runnable(){
            @Override
            public void run() {
                try{
                    //This is just to check if an exception was thrown
                    resourceRegistrationFuture.get();

                    //If there was no exception finalize registration process
                    observedResources.put(filePath, resourceUri);
                    log.info("Successfully registered resource {} from file {}.", resourceUri, filePath);

                    ChannelFuture future = cacheResourceStatus(resourceUri, model);
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if(future.isSuccess())
                                log.info("Succesfully stored status of {} in cache.", resourceUri);
                            else
                                log.error("Failed to store status of {} in cache.", resourceUri);
                        }
                    });
                }
                catch (Exception e) {
                    log.error("Exception while creating service to observe a file.", e);
                }
            }
        }, getScheduledExecutorService());
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

}

