package eu.spitfire.ssp.backend.files;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backend.generic.Registry;
import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Created by olli on 13.04.14.
 */
public class TurtleFilesRegistry extends Registry<Path, TurtleFile> {

    private static Logger LOG = LoggerFactory.getLogger(TurtleFilesRegistry.class.getName());

    private Path directory;
    private WatchService watchService;

    public TurtleFilesRegistry(TurtleFilesComponentFactory componentFactory) throws Exception {
        super(componentFactory);
        this.directory = componentFactory.getDirectoryPath();
        this.watchService = FileSystems.getDefault().newWatchService();
    }


    @Override
    public void startRegistry() throws Exception {
        final ScheduledExecutorService internalTasksExecutor = componentFactory.getInternalTasksExecutor();

        internalTasksExecutor.execute(new Runnable(){
            @Override
            public void run() {
                preWatchDirectory(directory);
            }
        });

        internalTasksExecutor.execute(new FileWatchingTask());
//        internalTasksExecutor.scheduleAtFixedRate(
//                new FileWatchingTask(), 1, 1, TimeUnit.SECONDS
//        );
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

                    LOG.info("Added directory to be watched: {}", directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attr) throws IOException {
                    if (filePath.toString().endsWith(".ttl")) {
                        try {
                            TurtleFile turtleFile = new TurtleFile(
                                    directoryPath, filePath, componentFactory.getHostName(), componentFactory.getPort());
                            registerDataOrigin(turtleFile);
                        } catch (URISyntaxException ex) {
                            LOG.error("This should never happen!", ex);
                        }
                    } else
                        LOG.debug("No Turtle file: \"{}\"", filePath);

                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            LOG.error("Exception while trying to start recursive observation of {}.", directoryPath, e);
        }
    }


    private void handleFileModification(final TurtleFile dataOrigin){
        LOG.info("File {} was updated!", dataOrigin);
        TurtleFilesAccessor accessor = (TurtleFilesAccessor) componentFactory.getAccessor(dataOrigin);
        Futures.addCallback(accessor.getStatus(dataOrigin), new FutureCallback<ExpiringNamedGraph>() {

            @Override
            public void onSuccess(ExpiringNamedGraph graph) {
                TurtleFilesObserver observer = (TurtleFilesObserver) componentFactory.getObserver(dataOrigin);
                observer.updateCache(graph);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Could not get updated status from file \"{}\"!", throwable);
            }
        });
    }


    private class FileWatchingTask implements Runnable{

        @Override
        public void run() {

            try{
                LinkedHashMultimap<TurtleFile, WatchEvent.Kind> detectedEvents = LinkedHashMultimap.create();
                LOG.debug("Time: " + System.currentTimeMillis());

                WatchKey watchKey = null;
                for(int i = 0; i < 2; i++) {
                    watchKey = watchService.poll();

                    if(watchKey == null){
                        continue;
                    }

                    Path directory = (Path) watchKey.watchable();

                    for (WatchEvent event : watchKey.pollEvents()) {

                        WatchEvent.Kind eventKind = event.kind();
                        final Path filePath = directory.resolve((Path) event.context());

                        //Handle events on directories
                        if (Files.isDirectory(filePath) && eventKind == ENTRY_CREATE) {
                            LOG.info("New directory \"{}\" created!", filePath);
                            filePath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                            continue;
                        }

                        //Ignore events on files whose names do not end with ".ttl"
                        if (!filePath.toString().endsWith(".ttl")) {
                            LOG.debug("Event on file {} will be ignored (no *.ttl)", filePath);
                            continue;
                        }

                        //Collect events on turtle files
                        TurtleFile turtleFile = new TurtleFile(
                                directory, filePath, componentFactory.getHostName(), componentFactory.getPort()
                        );
                        detectedEvents.put(turtleFile, eventKind);
                    }

                    // reset watchkey and remove from set if directory is no longer accessible
                    if (watchKey.reset()) {
                        LOG.info("Successfully reseted watchkey for directory {}.", directory);
                    } else {
                        LOG.error("Could not reset watchkey. Stop observation of directory {}", directory);
                        //TODO
                    }

                    Thread.sleep(200);
                }

                //handle events on turtle files
                if(!detectedEvents.isEmpty()) {
                    LOG.info("Number of Events: " + detectedEvents.size());
                    handleTurtleFileEvents(detectedEvents);
                    if(detectedEvents.size() != 3){
                        LOG.debug("...");
                    }
                }

//                // reset watchkey and remove from set if directory is no longer accessible
//                if(watchKey != null) {
//                    if (watchKey.reset()) {
//                        LOG.info("Successfully reseted watchkey for directory {}.", directory);
//                    } else {
//                        LOG.error("Could not reset watchkey. Stop observation of directory {}", directory);
//                        //TODO
//                    }
//                }
            }
            catch (Exception e) {
                LOG.error("This should never happen!", e);
            }

            componentFactory.getInternalTasksExecutor().schedule(new FileWatchingTask(), 1, TimeUnit.SECONDS);
        }


        private void handleTurtleFileEvents(LinkedHashMultimap<TurtleFile, WatchEvent.Kind> events){
            for(final TurtleFile file : events.keySet()){

                for(WatchEvent.Kind eventKind : events.get(file)){
                    LOG.info("Event {} on turtle file {}.", eventKind, file.getGraphName());
                }

                Iterator<WatchEvent.Kind> eventIterator = events.get(file).iterator();

                while(eventIterator.hasNext()){
                    WatchEvent.Kind eventKind = eventIterator.next();

                    if(eventKind == ENTRY_DELETE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_CREATE){
                            LOG.info("Turtle File {} was deleted and immediately recreated (UPDATE CACHE).",
                                    file.getGraphName());
                            handleFileModification(file);
                            break;
                        }
                        else{
                            LOG.info("File {} was deleted (UNREGISTER).", file.getGraphName());
                            unregisterDataOrigin(file.getIdentifier());
                            break;
                        }
                    }

                    else if(eventKind == ENTRY_CREATE){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            LOG.warn("File {} was created and immediately deleted (IGNORE)!", file.getGraphName());
                            break;
                        }
                        else if(eventIterator.hasNext() && eventIterator.next() == ENTRY_MODIFY){
                            LOG.info("Turtle File {} was created and modified (REGISTER).", file.getGraphName());
                            registerDataOrigin(file);
                            break;
                        }
                        else{
                            LOG.info("Turtle File {} was created (REGISTER).", file.getGraphName());
                            ListenableFuture<Void> regFuture = registerDataOrigin(file);
                            Futures.addCallback(regFuture, new FutureCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    //Nothing to do...
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    LOG.debug("Override...");
                                    handleFileModification(file);
                                }
                            });
                        }
                    }

                    else if(eventKind == ENTRY_MODIFY){
                        if(eventIterator.hasNext() && eventIterator.next() == ENTRY_DELETE){
                            LOG.warn("Turtle File {} was modified and immediately deleted (UNREGISTER)!", file);
                            unregisterDataOrigin(file.getIdentifier());
                            break;
                        }
                        else{
                            LOG.info("Turtle File {} was modified (UPDATE CACHE).", file);
                            handleFileModification(file);
                            break;
                        }
                    }

                    else{
                        LOG.error("Unexpected event on Turtle file {}: {}", file, eventKind);
                    }
                }
            }
        }
    }
}
