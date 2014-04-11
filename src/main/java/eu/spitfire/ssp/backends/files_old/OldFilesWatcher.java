package eu.spitfire.ssp.backends.files_old;

import eu.spitfire.ssp.backends.generic.registration.DataOriginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

import static java.nio.file.StandardWatchEventKinds.*;

/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 04.10.13
* Time: 18:04
* To change this template use File | Settings | File Templates.
*/
public class OldFilesWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    //private HashBasedTable <Path, Path, Long> fileModifications = HashBasedTable.create();
    //private Set<Path> watchedDirectories = new HashSet<>();

    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;

    private OldFilesBackendComponentFactory componentFactory;
    private DataOriginManager<Path> dataOriginManager;
    private OldFilesRegistry oldFilesRegistry;
//    private OldFilesObserver filesObserver;
    private ScheduledExecutorService scheduledExecutorService;



    public OldFilesWatcher(OldFilesBackendComponentFactory componentFactory) throws IOException {
        this.componentFactory = componentFactory;
        this.dataOriginManager = componentFactory.getDataOriginManager();
        this.watchService = componentFactory.getWatchService();
//        this.filesObserver = componentFactory.getFilesObserver();
        this.scheduledExecutorService = componentFactory.getExecutorService();
        this.watchKeys = Collections.synchronizedMap(new HashMap<WatchKey, Path>());
    }


    public void initialize(Path directory){
        oldFilesRegistry = (OldFilesRegistry) componentFactory.getDataOriginRegistry();
        watchDirectory(directory);
        scheduledExecutorService.submit(new FileWatcherTask());
        log.info("Started recursive observation of directory {}", directory);
    }


//    public Collection<Path> getWatchedDirectoriesWithFiles(){
//        return Arrays.asList(this.fileModifications.rowKeySet().toArray(new Path[0]));
//    }


    /**
     * The invocation of this method causes the given directory and all its subdirectories to be watched
     *
     * @param directoryPath the directory to be observed
     */
    private void watchDirectory(Path directoryPath){
        log.info("Start!");
        try{
            WatchKey watchKey = directoryPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchKeys.put(watchKey, directoryPath);
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
//                @Override
//                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
//                        throws IOException {
//
//                    WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
//                    watchKeys.put(watchKey, dir);
//
//                    log.info("Added directory to be watched: {}", dir);
//                    return FileVisitResult.CONTINUE;
//                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attr){
                    //If a file is no N3 file then ignore it
                    if(file.toString().endsWith(".n3")){
//                        fileModifications.put(file.getParent(), file.getParent().relativize(file),
//                                file.toFile().lastModified());
                        oldFilesRegistry.handleFileCreation(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }

    private class FileWatcherTask implements Runnable{

        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            while(true){
                try{
                    WatchKey watchKey = watchService.take();
                    Path directory = watchKeys.get(watchKey);

                    for(WatchEvent event : watchKey.pollEvents()){

                        WatchEvent.Kind eventKind = event.kind();
                        Path path = directory.resolve((Path) event.context());

                        log.info("Event {} at file {}", eventKind, path);

                        //Handle if the file is a directory
//                        if(Files.isDirectory(path) || fileModifications.rowKeySet().contains(path)){
//                            handleDirectoryEvent(eventKind, path);
//                        }

                        //If a file is no N3 file then ignore it
                        if(path.toString().endsWith(".n3")){
                            handleN3FileEvent(eventKind, path);
                        }
                    }

                    // reset watchkey and remove from set if directory is no longer accessible

                    if(watchKey.reset()){
                        log.info("Succesfully resetted watchkey for directory {}", watchKeys.get(watchKey));
                    }
                    else{
                        log.error("Could not reset Watchkey. Stopped observation of directory {}",
                                watchKeys.get(watchKey));
                        watchKeys.remove(watchKey);
                    }
                }
                catch (InterruptedException e) {
                    log.error("Exception while taking watch key.", e);
                }
            }
        }


        private void handleN3FileEvent(WatchEvent.Kind eventKind, Path file) throws InterruptedException {
            Path directory = file.getParent();

            if(eventKind == ENTRY_DELETE){
                //fileModifications.remove(file.getParent(), file.relativize(directory));
                filesObserver.handleFileDeletion(file);
                return;
            }

//            //Check last file modification (to avoid duplicate events)
//            long lastFileModification = file.toFile().lastModified();
//
//            if(fileModifications.get(directory, file.relativize(directory)) != null){
//                if(!(lastFileModification > fileModifications.get(directory, file.relativize(directory)))){
//                    log.info("Event duplicate on file {}. IGNORE!", file);
//                    return;
//                }
//            }
//
//            fileModifications.put(file.getParent(), file.relativize(directory), lastFileModification);

            else{

            Thread.sleep(1000);

                if(eventKind == ENTRY_CREATE){
                    if(dataOriginManager.getResources(file).isEmpty())
                        oldFilesRegistry.handleFileCreation(file);
                    else
                        filesObserver.handleFileModification(file);
                }

                else if(eventKind == ENTRY_MODIFY)
                    filesObserver.handleFileModification(file);

                else
                    log.error("Unhandled event kind OVERFLOW");
            }

        }
    }


//        private void handleDirectoryEvent(WatchEvent.Kind eventKind, Path directory){
//            if(eventKind == ENTRY_DELETE){
//                log.debug("Deleted directory: {}", directory);
//
//                for(Path otherDirectory : getWatchedDirectoriesWithFiles()){
//                    if(otherDirectory.startsWith(directory)){
//                        List<Path> files_old = Arrays.asList(fileModifications.row(directory).keySet().toArray(new Path[0]));
//                        for(Path file : files_old){
//                            fileModifications.remove(directory, file.relativize(otherDirectory));
//                            filesObserver.handleFileDeletion(directory.resolve(file));
//                        }
//                    }
//                }
//            }
//
//            else if(eventKind == ENTRY_CREATE){
//                watchDirectory(directory);
//            }
//
//            else{
//                log.error("Unsupported event kind ({}) for directory {}", eventKind, directory);
//            }
//        }
//    }
}
