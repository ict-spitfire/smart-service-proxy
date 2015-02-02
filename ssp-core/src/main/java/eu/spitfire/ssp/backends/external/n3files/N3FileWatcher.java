package eu.spitfire.ssp.backends.external.n3files;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

                LinkedHashMultimap<N3File, WatchEvent.Kind> detectedEvents = LinkedHashMultimap.create();

                for(WatchEvent event : watchKey.pollEvents()){

                    WatchEvent.Kind eventKind = event.kind();
                    final Path filePath = directory.resolve((Path) event.context());

                    //Handle events on directories
                    if(Files.isDirectory(filePath) && eventKind == ENTRY_CREATE){
                        log.info("New directory \"{}\" created!", filePath);
                        filePath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        continue;
                    }

                    //Ignore events on files whose names do not end with .n3
                    if (!filePath.toString().endsWith(".n3")){
                        log.debug("Event on file {} will be ignored (no *.n3)", filePath);
                        continue;
                    }

                    //Collect events on N3 files
                    N3File n3File = new N3File(filePath, componentFactory.getSspHostName());
                    log.debug("Event {} at file {}", eventKind, filePath);
                    detectedEvents.put(n3File, eventKind);
                }

                //handle events on N3 files
                handleN3FileEvents(detectedEvents);

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


        private void handleN3FileEvents(LinkedHashMultimap<N3File, WatchEvent.Kind> events){
            for(final N3File n3File : events.keySet()){

                for(WatchEvent.Kind eventKind : events.get(n3File)){
                    log.info("Event {} on N3 file {}.", eventKind, n3File);
                }

                Iterator<WatchEvent.Kind> eventIterator = events.get(n3File).iterator();

                while(eventIterator.hasNext()){
                    WatchEvent.Kind eventKind = eventIterator.next();

                    if(eventKind == ENTRY_DELETE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_CREATE){
                            log.info("N3 File {} was deleted and immediately recreated (UPDATE CACHE).", n3File);
                            handleFileModification(n3File);
                        }
                        else{
                            log.info("N3 File {} was deleted (UNREGISTER).", n3File);
                            registry.unregisterDataOrigin(n3File.getIdentifier());
                        }
                    }

                    else if(eventKind == ENTRY_CREATE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            log.warn("N3 File {} was created and immediately deleted(IGNORE)!", n3File);
                        }
                        else if(eventIterator.hasNext() && eventIterator.next() == ENTRY_MODIFY){
                            log.info("N3 File {} was created and modified (REGISTER).", n3File);
                            registry.registerDataOrigin(n3File);
                        }
                        else{
                            log.info("N3 File {} was created (REGISTER).", n3File);
                            ListenableFuture<Void> regFuture = registry.registerDataOrigin(n3File);
                            Futures.addCallback(regFuture, new FutureCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    //Nothing to do...
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    handleFileModification(n3File);
                                }
                            });
                        }
                    }

                    else if(eventKind == ENTRY_MODIFY){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            log.warn("N3 File {} was modified and immediately deleted (UNREGISTER)!", n3File);
                            registry.unregisterDataOrigin(n3File.getIdentifier());
                        }
                        else{
                            log.info("N3 File {} was modified (UPDATE CACHE).", n3File);
                            handleFileModification(n3File);
                        }
                    }

                    else{
                        log.error("Unexpected event on N3 file {}: {}", n3File, eventKind);
                    }
                }
            }
        }
    }
}
