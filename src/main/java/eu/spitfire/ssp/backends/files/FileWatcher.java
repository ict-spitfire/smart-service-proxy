package eu.spitfire.ssp.backends.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

/**
 * Created by olli on 15.04.14.
 */
class FileWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private FileRegistry fileRegistry;
    private FileObserver fileObserver;

    private WatchService watchService;
    private FilesBackendComponentFactory componentFactory;

    public FileWatcher(FilesBackendComponentFactory componentFactory) throws Exception {
        this.componentFactory = componentFactory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.fileRegistry = (FileRegistry) componentFactory.getDataOriginRegistry();
        this.fileObserver = componentFactory.getDataOriginObserver(null);
    }


    void startFileWatching(){
        Path rootDirectory = new File(componentFactory.getConfig().getString("directory")).toPath();
        preWatchDirectory(rootDirectory);

        ScheduledExecutorService backendTasksExecutorService = componentFactory.getBackendTasksExecutorService();
        backendTasksExecutorService.scheduleAtFixedRate(new FileWatchingTask(), 100, 100, TimeUnit.MILLISECONDS);
    }


    /**
     * The invocation of this method causes the given directory and all its subdirectories to be watched
     *
     * @param directoryPath the directory to be observed
     */
    private void preWatchDirectory(Path directoryPath){
        log.info("Start!");
        try{
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                        throws IOException {

                    directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE);

                    log.info("Added directory to be watched: {}", directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                    fileRegistry.handleFileCreation(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }


    private class FileWatchingTask implements Runnable{

        @Override
        public void run() {
            try{
                WatchKey watchKey = watchService.take();
                Path directory = (Path) watchKey.watchable();

                for(WatchEvent event : watchKey.pollEvents()){

                    WatchEvent.Kind eventKind = event.kind();
                    Path file = directory.resolve((Path) event.context());

                    log.info("Event {} at file {}", eventKind, file);

                    //Handle if the file is a directory
                    if(eventKind == StandardWatchEventKinds.ENTRY_CREATE){
                        if(Files.isDirectory(file)){
                            log.info("New directory \"{}\" created!", file);
                            file.register(watchService, ENTRY_CREATE, ENTRY_DELETE);
                        }
                        else{
                            fileRegistry.handleFileCreation(file);
                        }
                    }
                    else if(eventKind == StandardWatchEventKinds.ENTRY_DELETE && Files.isRegularFile(file)){
                        fileRegistry.removeDataOrigin(file);
                    }
                    else if(eventKind == StandardWatchEventKinds.ENTRY_MODIFY && Files.isRegularFile(file)){
                        fileObserver.updateDetected(file);
                    }
                }

                // reset watchkey and remove from set if directory is no longer accessible
                if(watchKey.reset()){
                    log.info("Successfully resetted watchkey for directory {}.", directory);
                }
                else{
                    log.error("Could not reset watchkey. Stop observation of directory {}", directory);
                    //TODO
                }
            }
            catch (Exception e) {
                log.error("This should never happen!", e);
            }
        }
    }
}
