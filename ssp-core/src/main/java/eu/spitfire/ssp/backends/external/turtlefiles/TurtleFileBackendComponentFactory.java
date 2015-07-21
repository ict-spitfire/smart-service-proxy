package eu.spitfire.ssp.backends.external.turtlefiles;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 11.04.14.
 */
public class TurtleFileBackendComponentFactory extends BackendComponentFactory<Path, TurtleFile> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private TurtleFileAccessor turtleFileAccessor;
    private TurtleFileObserver turtleFileObserver;
    private Path directoryPath;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param config          the SSP config
     * @param backendTasksExecutorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                    e.g. translating and forwarding requests to data origins
     *
     * @throws Exception if something went terribly wrong
     */
    public TurtleFileBackendComponentFactory(Configuration config, LocalServerChannel localChannel,
                                             ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super("turtlefiles", config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.directoryPath = new File(config.getString("turtlefiles.directory")).toPath();
    }


    @Override
    public void initialize() throws Exception{
        this.turtleFileAccessor = new TurtleFileAccessor(this);
        this.turtleFileObserver = new TurtleFileObserver(this);
        new TurtleFileWatcher(this).startFileWatching(directoryPath);
    }


    @Override
    public TurtleFileObserver getObserver(TurtleFile dataOrigin) {
        return this.turtleFileObserver;
    }


    @Override
    public TurtleFileAccessor getAccessor(TurtleFile dataOrigin) {
        return this.turtleFileAccessor;
    }

    @Override
    public TurtleFileRegistry getRegistry() {
        return (TurtleFileRegistry) super.getRegistry();
    }


    @Override
    public TurtleFileRegistry createRegistry(Configuration config) throws Exception {
        return new TurtleFileRegistry(this);
    }


    @Override
    public void shutdown() {
        //TODO
    }

    public Path getDirectoryPath() {
        return directoryPath;
    }

//    public String getSspHostName() {
//        return sspHostName;
//    }
}
