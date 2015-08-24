package eu.spitfire.ssp.backend.files;

import eu.spitfire.ssp.backend.generic.ComponentFactory;
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
public class TurtleFilesComponentFactory extends ComponentFactory<Path, TurtleFile> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private TurtleFilesAccessor turtleFilesAccessor;
    private TurtleFilesObserver turtleFilesObserver;
    private Path directoryPath;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.ComponentFactory}.
     *
     * @param config          the SSP config
     * @param backendTasksExecutorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                    e.g. translating and forwarding requests to data origins
     *
     * @throws Exception if something went terribly wrong
     */
    public TurtleFilesComponentFactory(Configuration config, LocalServerChannel localChannel,
            ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super("files", config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.directoryPath = new File(config.getString("files.directory")).toPath();
    }


    @Override
    public void initialize() throws Exception{
        this.turtleFilesAccessor = new TurtleFilesAccessor(this);
        this.turtleFilesObserver = new TurtleFilesObserver(this);
    }


    @Override
    public TurtleFilesObserver getObserver(TurtleFile dataOrigin) {
        return this.turtleFilesObserver;
    }


    @Override
    public TurtleFilesAccessor getAccessor(TurtleFile dataOrigin) {
        return this.turtleFilesAccessor;
    }

    @Override
    public TurtleFilesRegistry getRegistry() {
        return (TurtleFilesRegistry) super.getRegistry();
    }


    @Override
    public TurtleFilesRegistry createRegistry(Configuration config) throws Exception {
        return new TurtleFilesRegistry(this);
    }


    @Override
    public void shutdown() {
        //TODO
    }

    public Path getDirectoryPath() {
        return directoryPath;
    }

//    public String getHostName() {
//        return sspHostName;
//    }
}
