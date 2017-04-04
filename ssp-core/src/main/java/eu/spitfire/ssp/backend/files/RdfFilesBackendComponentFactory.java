package eu.spitfire.ssp.backend.files;

import eu.spitfire.ssp.backend.generic.BackendComponentFactory;
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
public class RdfFilesBackendComponentFactory extends BackendComponentFactory<Path, RdfFile> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private RdfFileAccessor rdfFileAccessor;
    private RdfFilesObserver turtleFilesObserver;
    private Path directoryPath;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.BackendComponentFactory}.
     *
     * @param config          the SSP config
     * @param backendTasksExecutorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                    e.g. translating and forwarding requests to data origins
     *
     * @throws Exception if something went terribly wrong
     */
    public RdfFilesBackendComponentFactory(Configuration config, LocalServerChannel localChannel,
                                           ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super("files", config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.directoryPath = new File(config.getString("files.directory")).toPath();
    }


    @Override
    public void initialize() throws Exception{
        this.rdfFileAccessor = new RdfFileAccessor(this);
        this.turtleFilesObserver = new RdfFilesObserver(this);
    }


    @Override
    public RdfFilesObserver getObserver(RdfFile dataOrigin) {
        return this.turtleFilesObserver;
    }


    @Override
    public RdfFileAccessor getAccessor(RdfFile dataOrigin) {
        return this.rdfFileAccessor;
    }

    @Override
    public RdfFilesRegistry getRegistry() {
        return (RdfFilesRegistry) super.getRegistry();
    }


    @Override
    public RdfFilesRegistry createRegistry(Configuration config) throws Exception {
        return new RdfFilesRegistry(this);
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
