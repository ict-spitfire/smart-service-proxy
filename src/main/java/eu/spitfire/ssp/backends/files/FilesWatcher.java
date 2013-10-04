package eu.spitfire.ssp.backends.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 18:04
 * To change this template use File | Settings | File Templates.
 */
public class FilesWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;

    private FilesBackendComponentFactory backendComponentFactory;
    private FilesRegistry filesRegistry;
    private FilesObserver filesObserver;
    private ScheduledExecutorService scheduledExecutorService;

    public FilesWatcher(FilesBackendComponentFactory backendComponentFactory) throws IOException {
        this.backendComponentFactory = backendComponentFactory;
        this.watchService = backendComponentFactory.getWatchService();
        this.filesObserver = backendComponentFactory.getFilesObserver();
        this.scheduledExecutorService = backendComponentFactory.getScheduledExecutorService();
        this.watchKeys = Collections.synchronizedMap(new HashMap<WatchKey, Path>());
    }

    public void initialize(Path directory){
        observeDirectoryRecursively(directory);
        filesRegistry = (FilesRegistry) backendComponentFactory.getDataOriginRegistry();
        scheduledExecutorService.submit(new FileWatcherTask());
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
                        throws IOException {

                    WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    watchKeys.put(watchKey, dir);

                    log.info("Added directory to be watched: {}", dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }

    private class FileWatcherTask implements Runnable{

        @Override
        public void run() {
            while(true){
                log.info("Observe...");
                try{
                    WatchKey watchKey = watchService.take();
                    Path directory = watchKeys.get(watchKey);
                    for(WatchEvent ev : watchKey.pollEvents()){
                        //Which kind of event occurred?
                        WatchEvent.Kind eventKind = ev.kind();

                        Path filePath = directory.resolve((Path) ev.context());
                        log.info("Event {} at path {}:", eventKind, filePath);

                        if(eventKind == ENTRY_CREATE)
                            filesRegistry.handleFileCreation(filePath);

                        else if(eventKind == ENTRY_MODIFY)
                            filesObserver.handleFileModification(filePath);

                        else if(eventKind == ENTRY_DELETE)
                            filesObserver.handleFileDeletion(filePath);

                        else if(eventKind == OVERFLOW)
                            log.warn("Unhandled event kind OVERFLOW");

                        else
                            log.error("Unknown event kind {}", eventKind);
                    }
                    // reset key and remove from set if directory is no longer accessible
                    if (!watchKey.reset()) {
                        log.error("Stopped observation of directory {}.", watchKeys.get(watchKey));
                        watchKeys.remove(watchKey);
                    }
                }
                catch (InterruptedException e) {
                    log.error("Exception while taking watch key.", e);
                }
            }
        }
    }
}
