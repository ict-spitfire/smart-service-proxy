package eu.spitfire.ssp.backends.external.n3files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.spitfire.ssp.backends.DataOriginAccessResult;
import eu.spitfire.ssp.server.internal.messages.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by olli on 15.04.14.
 */
class N3FileWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private N3FileRegistry registry;
    private N3FileObserver observer;
    private N3FileAccessor accessor;

    private WatchService watchService;
    private N3FileBackendComponentFactory componentFactory;

    public N3FileWatcher(N3FileBackendComponentFactory componentFactory) throws Exception {
        this.componentFactory = componentFactory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.registry = componentFactory.getRegistry();
        this.observer = componentFactory.getObserver(null);
        this.accessor = componentFactory.getAccessor(null);
    }


    void startFileWatching(){
        Path rootDirectory = new File(componentFactory.getConfig().getString("directory")).toPath();
        preWatchDirectory(rootDirectory);

        ScheduledExecutorService backendTasksExecutorService = componentFactory.getInternalTasksExecutor();
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

                    directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

                    log.info("Added directory to be watched: {}", directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attr) throws IOException {
                    if(filePath.toString().endsWith(".n3")){
                        try{
                            N3File n3File = new N3File(filePath, componentFactory.getSspHostName());
                            registry.registerDataOrigin(n3File);
                        }
                        catch(URISyntaxException ex){
                            log.error("This should never happen!", ex);
                        }
                    }
                    else
                        log.debug("No N3 file: \"{}\"", filePath);

                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }


    private void handleFileModification(final N3File dataOrigin){
        log.info("File {} was updated!", dataOrigin);
        Futures.addCallback(accessor.getStatus(dataOrigin), new FutureCallback<DataOriginAccessResult>() {

            @Override
            public void onSuccess(DataOriginAccessResult accessResult) {
                if (accessResult instanceof ExpiringNamedGraph) {
                    observer.updateCache((ExpiringNamedGraph) accessResult);
                }
                else {
                    log.error("Data Origin {} did not return an expiring named graph status but {}",
                            dataOrigin, accessResult);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("Could not get updated status from file \"{}\"!", throwable);
            }
        });
    }


    private class FileWatchingTask implements Runnable{

        @Override
        public void run() {
            try{
                WatchKey watchKey = watchService.take();
                Path directory = (Path) watchKey.watchable();

                for(WatchEvent event : watchKey.pollEvents()){

                    WatchEvent.Kind eventKind = event.kind();
                    final Path filePath = directory.resolve((Path) event.context());

                    log.info("Event {} at file {}", eventKind, filePath);

                    if(eventKind == StandardWatchEventKinds.ENTRY_CREATE){

                        if(Files.isDirectory(filePath)){
                            log.info("New directory \"{}\" created!", filePath);
                            filePath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        }

                        else if (filePath.toString().endsWith(".n3")){
                            try{
                                N3File n3File = new N3File(filePath, componentFactory.getSspHostName());
                                registry.registerDataOrigin(n3File);
                            }
                            catch(URISyntaxException ex){
                                log.error("This should never happen!", ex);
                            }
                        }
                    }

                    else if(eventKind == StandardWatchEventKinds.ENTRY_DELETE && (filePath.toString().endsWith(".n3"))){
                        registry.unregisterDataOrigin(filePath);
                    }

                    else if(eventKind == StandardWatchEventKinds.ENTRY_MODIFY && (filePath.toString().endsWith(".n3"))){
                        N3File dataOrigin = componentFactory.getDataOriginMapper().getDataOrigin(filePath);
                        handleFileModification(dataOrigin);
                    }

                    else{
                        log.warn("Don't know what to do (Event {} on file \"{}\").", eventKind, filePath);
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
