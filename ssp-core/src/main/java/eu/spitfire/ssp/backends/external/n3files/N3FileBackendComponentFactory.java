package eu.spitfire.ssp.backends.external.n3files;

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
public class N3FileBackendComponentFactory extends BackendComponentFactory<Path, N3File> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private N3FileAccessor n3FileAccessor;
    private N3FileObserver n3FileObserver;
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
    public N3FileBackendComponentFactory(Configuration config, LocalServerChannel localChannel,
                                         ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super("n3files", config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.directoryPath = new File(config.getString("n3files.directory")).toPath();
    }


    @Override
    public void initialize() throws Exception{
        this.n3FileAccessor = new N3FileAccessor(this);
        this.n3FileObserver = new N3FileObserver(this);
        new N3FileWatcher(this).startFileWatching(directoryPath);
    }


    @Override
    public N3FileObserver getObserver(N3File dataOrigin) {
        return this.n3FileObserver;
    }


    @Override
    public N3FileAccessor getAccessor(N3File dataOrigin) {
        return this.n3FileAccessor;
    }

    @Override
    public N3FileRegistry getRegistry() {
        return (N3FileRegistry) super.getRegistry();
    }


    @Override
    public N3FileRegistry createRegistry(Configuration config) throws Exception {
        return new N3FileRegistry(this);
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
