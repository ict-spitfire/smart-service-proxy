package eu.spitfire.ssp.backends.n3files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.spitfire.ssp.utils.exceptions.IdentifierAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Created by olli on 15.04.14.
 */
class N3FileWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private N3FileRegistry n3FileRegistry;
    private N3FileObserver n3FileObserver;

    private WatchService watchService;
    private N3FileBackendComponentFactory componentFactory;

    public N3FileWatcher(N3FileBackendComponentFactory componentFactory) throws Exception {
        this.componentFactory = componentFactory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.n3FileRegistry = (N3FileRegistry) componentFactory.getRegistry();
        this.n3FileObserver = componentFactory.getObserver(null);
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                    if(file.toString().endsWith(".n3"))
                        n3FileRegistry.handleN3FileCreation(file);
                    else
                        log.debug("No N3 file: \"{}\"", file);

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
                    final Path file = directory.resolve((Path) event.context());

                    log.info("Event {} at file {}", eventKind, file);

                    if(eventKind == StandardWatchEventKinds.ENTRY_CREATE){

                        if(Files.isDirectory(file)){
                            log.info("New directory \"{}\" created!", file);
                            file.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        }

                        else if (file.toString().endsWith(".n3")){
                            Futures.addCallback(n3FileRegistry.handleN3FileCreation(file), new FutureCallback<Void>() {

                                @Override
                                public void onSuccess(Void aVoid) {
                                    log.info("Successfully handled creation of new file \"{}\".", file);
                                }


                                @Override
                                public void onFailure(Throwable throwable) {
                                    if(throwable instanceof IdentifierAlreadyRegisteredException){
                                        N3File dataOrigin = (N3File) N3FileWatcher.this.componentFactory
                                                .getProtocolCastingWebservice().getDataOrigin(file);

                                        n3FileObserver.updateDetected(dataOrigin);
                                    }
                                }
                            });
                        }
                    }

                    else if(eventKind == StandardWatchEventKinds.ENTRY_DELETE && (file.toString().endsWith(".n3"))){

                        Futures.addCallback(n3FileRegistry.handleN3FileDeletion(file), new FutureCallback<Void>() {

                            @Override
                            public void onSuccess(Void aVoid) {
                                log.info("Successfully handled deletion of file \"{}\".", file);
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                log.error("Error while handling deletion of file \"{}\".", file, throwable);
                            }
                        });
                    }

                    else if(eventKind == StandardWatchEventKinds.ENTRY_MODIFY && (file.toString().endsWith(".n3"))){
                        N3File dataOrigin = (N3File) N3FileWatcher.this.componentFactory
                                .getProtocolCastingWebservice().getDataOrigin(file);

                        n3FileObserver.updateDetected(dataOrigin);
                    }

                    else{
                        log.warn("Don't know what to do (Event {} on file \"{}\").", eventKind, file);
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
