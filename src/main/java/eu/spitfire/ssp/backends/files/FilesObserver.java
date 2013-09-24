//package eu.spitfire.ssp.backends.files;
//
//import com.google.common.collect.HashMultimap;
//import com.google.common.collect.Multimap;
//import com.google.common.collect.Multimaps;
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.rdf.model.*;
//import eu.spitfire.ssp.backends.utils.DataOriginObserver;
//import eu.spitfire.ssp.backends.utils.ResourceToolbox;
//import org.jboss.netty.channel.ChannelFuture;
//import org.jboss.netty.channel.ChannelFutureListener;
//import org.jboss.netty.channel.local.LocalServerChannel;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.net.URI;
//import java.nio.file.*;
//import java.nio.file.attribute.BasicFileAttributes;
//import java.util.*;
//import java.util.concurrent.ScheduledExecutorService;
//
//import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
//import static java.nio.file.StandardWatchEventKinds.*;
//
///**
// * A {@link FilesObserver} observes a given directory to be aware of creation, modification, and deletion of
// * files containing semantic data or directories.
// *
// * Currently, only N3 is supported as serialization type contained in the files. A file may contain one or multiple
// * resources (i.e. subjects). However, one proxy service is created for each resource contained in the
// * file.
// *
// * Since the {@link FilesObserver} only knows the file but not the resource whose status was changed, it is recommended
// * to have only one resource per file since the {@link FilesObserver} tries to
// * update every resource contained in a file whenever that file changes.
// *
// * @author Oliver Kleine
// */
//public class FilesObserver extends DataOriginObserver implements Runnable{
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//    private WatchService watchService;
//    private Map<WatchKey, Path> watchKeys;
//    private Multimap<Path, URI> observedResources;
//    private FilesBackendManager serviceManager;
//    private Path observedDirectory;
//    private HttpRequestProcessorForFiles requestProcessor;
//
//    /**
//     * @param serviceManager The {@link FilesBackendManager} that is responsible for registration and maintainance of
//     *                       resources backed by local files
//     * @param directory The {@link Path} representing the root-directory whose files and sub-directories are to be
//     *                  observed
//     * @param scheduledExecutorService the {@link ScheduledExecutorService} to execute tasks for resource management
//     * @param localServerChannel the {@link LocalServerChannel} to send internal messages, e.g. for resource registration
//     *
//     * @throws IOException if some I/O error occurred during the creation of the observation services
//     */
//    public FilesObserver(FilesBackendManager serviceManager, Path directory,
//                         ScheduledExecutorService scheduledExecutorService,
//                         LocalServerChannel localServerChannel,
//                         HttpRequestProcessorForFiles requestProcessor) throws IOException {
//
//        super(scheduledExecutorService, localServerChannel);
//
//        this.observedDirectory = directory;
//        this.requestProcessor = requestProcessor;
//        this.watchService = FileSystems.getDefault().newWatchService();
//        this.watchKeys = Collections.synchronizedMap(new HashMap<WatchKey, Path>());
//        this.observedResources = Multimaps.synchronizedMultimap(HashMultimap.<Path, URI>create());
//        this.serviceManager = serviceManager;
//
//        observeDirectoryRecursively(directory);
//    }
//
//    /**
//     * Method to observe the directory given as constructor parameter. It handles all possible events (creation,
//     * deletion, and modification).
//     */
//    @Override
//    public void run() {
//        while(true){
//            try{
//                WatchKey watchKey = watchService.take();
//
//                Path directory = watchKeys.get(watchKey);
//
//                for(WatchEvent ev : watchKey.pollEvents()){
//                    //Which kind of event occurred?
//                    WatchEvent.Kind eventKind = ev.kind();
//
//                    Path filePath = directory.resolve((Path) ev.context());
//                    log.debug("Event {} at path {}", eventKind, filePath);
//
//                    if(eventKind == ENTRY_CREATE || eventKind == ENTRY_MODIFY){
//                        handleFileCreationOrModification(filePath);
//                    }
//                    else if(eventKind == ENTRY_DELETE){
//                        handleFileDeletion(filePath);
//                    }
//                    if(eventKind == OVERFLOW){
//                        log.warn("Unhandled event kind OVERFLOW");
//                    }
//                }
//
//                // reset key and remove from set if directory is no longer accessible
//                boolean valid = watchKey.reset();
//                if (!valid) {
//                    log.error("Stopped observation of directory {}.", watchKeys.get(watchKey));
//                    watchKeys.remove(watchKey);
//                }
//            }
//            catch (InterruptedException e) {
//                log.error("Exception while taking watch key.", e);
//                continue;
//            }
//        }
//    }
//
//    /**
//     * The invocation of this method causes the given directory and all its subdirectories to be observed
//     *
//     * @param directoryPath the directory to be observed
//     */
//    private void observeDirectoryRecursively(Path directoryPath){
//        try{
//            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
//                @Override
//                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
//                        throws IOException{
//
//                    WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
//                    watchKeys.put(watchKey, dir);
//
//                    log.info("Added directory to be observed: {}", dir);
//                    return FileVisitResult.CONTINUE;
//                }
//            });
//        } catch (IOException e) {
//            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
//        }
//    }
//
//    private void handleFileCreationOrModification(final Path filePath){
//        //If a new directory was created then try to add it
//        if (Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
//            observeDirectoryRecursively(filePath);
//            return;
//        }
//
//        //If a file is no N3 file then ignore it
//        if(!filePath.toString().endsWith(".n3")){
//            log.debug("File is no N3 file {}. IGNORE!", filePath);
//            return;
//        }
//
//        Map<URI, Model> resourcesFromFile = ResourceToolbox.readResourcesFromFile(filePath);
//
//        //Add all resources from file to HTTP request processor
//        for(URI resourceURI : resourcesFromFile.keySet()){
//            requestProcessor.addResource(resourceURI, filePath);
//        }
//
//        //Detect resources which where included in the previous version of this file but not in the new version
//        List<URI> deletedResourceUris = new ArrayList<>();
//        for(URI resourceUri : observedResources.get(filePath).toArray(new URI[0])){
//            if(!resourcesFromFile.keySet().contains(resourceUri)){
//                log.debug("Resource {} was deleted from file!", resourceUri);
//                deletedResourceUris.add(resourceUri);
//            }
//        }
//
//        //Delete resources which where included in the previous version of this file but not in the new version
//        for(URI resourceUri : deletedResourceUris){
//            removeResourceStatusFromCache(resourceUri);
//            observedResources.remove(filePath, resourceUri);
//            requestProcessor.removeResource(resourceUri);
//        }
//
//        //Register or update resources from file (expiry is in ~1 year)
//        for(URI resourceUri : resourcesFromFile.keySet()){
//            if(observedResources.values().contains(resourceUri)){
//                cacheResourceStatus(resourceUri, resourcesFromFile.get(resourceUri));
//            }
//            else{
//                registerResource(filePath, resourceUri, resourcesFromFile.get(resourceUri));
//            }
//        }
//    }
//
//    private void handleFileDeletion(Path filePath){
//        //If a new directory was created then try to add it
//        if (Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
//            log.info("Nothing to do when a directory was deleted ({}).", filePath);
//            return;
//        }
//
//        Collection<URI> deletedResources = observedResources.get(filePath);
//        for(URI resourceUri : deletedResources){
//            removeResourceStatusFromCache(resourceUri);
//        }
//
//        observedResources.removeAll(filePath);
//    }
//
//    private void registerResource(final Path filePath, final URI resourceUri, final Model model){
//        final SettableFuture<URI> resourceRegistrationFuture = serviceManager.registerResource(resourceUri);
//        resourceRegistrationFuture.addListener(new Runnable(){
//            @Override
//            public void run() {
//                try{
//                    //This is just to check if an exception was thrown
//                    resourceRegistrationFuture.get();
//
//                    //If there was no exception finalize registration process
//                    observedResources.put(filePath, resourceUri);
//                    log.info("Successfully registered resource {} from file {}.", resourceUri, filePath);
//
//                    ChannelFuture future = cacheResourceStatus(resourceUri, model);
//                    future.addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture future) throws Exception {
//                            if(future.isSuccess())
//                                log.debug("Succesfully stored status of {} in cache.", resourceUri);
//                            else
//                                log.error("Failed to store status of {} in cache.", resourceUri);
//                        }
//                    });
//                }
//                catch (Exception e) {
//                    log.error("Exception while creating service to observe a file.", e);
//                }
//            }
//        }, getScheduledExecutorService());
//    }
//
//    /**
//     * Returns a {@link Collection} containing all the observed directories
//     * @return a {@link Collection} containing all the observed directories
//     */
//    public Collection<Path> getObservedDirectories(){
//        return watchKeys.values();
//    }
//
//    /**
//     * Returns a {@link Collection} containing all files (no directories) in all observed (sub-)directories
//     * @return a {@link Collection} containing all files (no directories) in all observed (sub-)directories
//     */
//    public Collection<Path> getObservedFiles(){
//        return this.observedResources.keySet();
//    }
//
//    /**
//     * Returns a {@link Collection} containing all observed resources (i.e. subjects) in the given file
//     * @param file the {@link Path} representing the path to the file on the local file-system
//     * @return a {@link Collection} containing all observed resources (i.e. subjects) in the given file
//     */
//    public Collection<URI> getObservedResources(Path file){
//        return this.observedResources.get(file);
//    }
//
//    /**
//     * Returns the {@link Path} that represents the observed root-directory (defined in <code>ssp.properties</code>)
//     * @return the {@link Path} that represents the observed root-directory (defined in <code>ssp.properties</code>)
//     */
//    public Path getObservedDirectory(){
//        return this.observedDirectory;
//    }
//}
//
