package eu.spitfire.ssp.backends.external.turtlefiles;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by olli on 15.04.14.
 */
class TurtleFileWatcher {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private TurtleFileRegistry registry;
    private TurtleFileObserver observer;
    private TurtleFileAccessor accessor;

    private WatchService watchService;
    private TurtleFileBackendComponentFactory componentFactory;

    public TurtleFileWatcher(TurtleFileBackendComponentFactory componentFactory) throws Exception {
        this.componentFactory = componentFactory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.registry = componentFactory.getRegistry();
        this.observer = componentFactory.getObserver(null);
        this.accessor = componentFactory.getAccessor(null);
    }


    void startFileWatching(Path directoryPath){
        final Path rootDirectory = directoryPath;
        final ScheduledExecutorService internalTasksExecutor = componentFactory.getInternalTasksExecutor();

        internalTasksExecutor.execute(new Runnable(){

            @Override
            public void run() {
                preWatchDirectory(rootDirectory);
            }
        });

        internalTasksExecutor.scheduleAtFixedRate(
                new FileWatchingTask(), 100, 100, TimeUnit.MILLISECONDS
        );
    }


    /**
     * The invocation of this method causes the given directory and all its subdirectories to be watched
     *
     * @param directoryPath the directory to be observed
     */
    private void preWatchDirectory(Path directoryPath){
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
                    if(filePath.toString().endsWith(".ttl")){
                        try{
                            TurtleFile turtleFile = new TurtleFile(filePath, componentFactory.getSspHostName());
                            registry.registerDataOrigin(turtleFile);
                        }
                        catch(URISyntaxException ex){
                            log.error("This should never happen!", ex);
                        }
                    }
                    else
                        log.debug("No Turtle file: \"{}\"", filePath);

                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }


    private void handleFileModification(final TurtleFile dataOrigin){
        log.info("File {} was updated!", dataOrigin);
        Futures.addCallback(accessor.getStatus(dataOrigin), new FutureCallback<DataOriginInquiryResult>() {

            @Override
            public void onSuccess(DataOriginInquiryResult accessResult) {
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

                LinkedHashMultimap<TurtleFile, WatchEvent.Kind> detectedEvents = LinkedHashMultimap.create();

                for(WatchEvent event : watchKey.pollEvents()){

                    WatchEvent.Kind eventKind = event.kind();
                    final Path filePath = directory.resolve((Path) event.context());

                    //Handle events on directories
                    if(Files.isDirectory(filePath) && eventKind == ENTRY_CREATE){
                        log.info("New directory \"{}\" created!", filePath);
                        filePath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        continue;
                    }

                    //Ignore events on files whose names do not end with ".ttl"
                    if (!filePath.toString().endsWith(".ttl")){
                        log.debug("Event on file {} will be ignored (no *.ttl)", filePath);
                        continue;
                    }

                    //Collect events on turtle files
                    TurtleFile turtleFile = new TurtleFile(filePath, componentFactory.getSspHostName());
                    log.debug("Event {} at file {}", eventKind, filePath);
                    detectedEvents.put(turtleFile, eventKind);
                }

                //handle events on turtle files
                handleTurtleFileEvents(detectedEvents);

                // reset watchkey and remove from set if directory is no longer accessible
                if(watchKey.reset()){
                    log.info("Successfully reseted watchkey for directory {}.", directory);
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


        private void handleTurtleFileEvents(LinkedHashMultimap<TurtleFile, WatchEvent.Kind> events){
            for(final TurtleFile turtleFile : events.keySet()){

                for(WatchEvent.Kind eventKind : events.get(turtleFile)){
                    log.info("Event {} on turtle file {}.", eventKind, turtleFile);
                }

                Iterator<WatchEvent.Kind> eventIterator = events.get(turtleFile).iterator();

                while(eventIterator.hasNext()){
                    WatchEvent.Kind eventKind = eventIterator.next();

                    if(eventKind == ENTRY_DELETE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_CREATE){
                            log.info("Turtle File {} was deleted and immediately recreated (UPDATE CACHE).", turtleFile);
                            handleFileModification(turtleFile);
                        }
                        else{
                            log.info("Turtle File {} was deleted (UNREGISTER).", turtleFile);
                            registry.unregisterDataOrigin(turtleFile.getIdentifier());
                        }
                    }

                    else if(eventKind == ENTRY_CREATE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            log.warn("Turtle File {} was created and immediately deleted(IGNORE)!", turtleFile);
                        }
                        else if(eventIterator.hasNext() && eventIterator.next() == ENTRY_MODIFY){
                            log.info("Turtle File {} was created and modified (REGISTER).", turtleFile);
                            registry.registerDataOrigin(turtleFile);
                        }
                        else{
                            log.info("Turtle File {} was created (REGISTER).", turtleFile);
                            ListenableFuture<Void> regFuture = registry.registerDataOrigin(turtleFile);
                            Futures.addCallback(regFuture, new FutureCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    //Nothing to do...
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    handleFileModification(turtleFile);
                                }
                            });
                        }
                    }

                    else if(eventKind == ENTRY_MODIFY){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            log.warn("Turtle File {} was modified and immediately deleted (UNREGISTER)!", turtleFile);
                            registry.unregisterDataOrigin(turtleFile.getIdentifier());
                        }
                        else{
                            log.info("Turtle File {} was modified (UPDATE CACHE).", turtleFile);
                            handleFileModification(turtleFile);
                        }
                    }

                    else{
                        log.error("Unexpected event on Turtle file {}: {}", turtleFile, eventKind);
                    }
                }
            }
        }
    }
}
