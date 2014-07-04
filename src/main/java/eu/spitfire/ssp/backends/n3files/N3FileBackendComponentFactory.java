package eu.spitfire.ssp.backends.n3files;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 11.04.14.
 */
public class N3FileBackendComponentFactory extends BackendComponentFactory<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private N3FileAccessor n3FileAccessor;
    private N3FileObserver n3FileObserver;
    private WatchService watchService;
    private Configuration config;
    private String sspHostName;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix          the prefix of the backend in the given config (without the ".")
     * @param config          the SSP config
     * @param backendTasksExecutorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                    e.g. translating and forwarding requests to data origins
     *
     * @throws Exception if something went terribly wrong
     */
    public N3FileBackendComponentFactory(String prefix, Configuration config, LocalServerChannel localChannel,
                                         ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super(prefix, config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.sspHostName = config.getString("SSP_HOST_NAME");
        this.config = config.subset(prefix);

    }


    @Override
    public void initialize() throws Exception{
        this.watchService = FileSystems.getDefault().newWatchService();

        this.n3FileAccessor = new N3FileAccessor(this);
        this.n3FileObserver = new N3FileObserver(this);

        N3FileWatcher n3FileWatcher = new N3FileWatcher(this);
        n3FileWatcher.startFileWatching();

    }


    Configuration getConfig(){
        return this.config;
    }


    @Override
    public N3FileObserver getObserver(DataOrigin<Path> dataOrigin) {
        return this.n3FileObserver;
    }


    @Override
    public N3FileAccessor getAccessor(DataOrigin<Path> dataOrigin) {
        return this.n3FileAccessor;
    }


    @Override
    public N3FileRegistry createRegistry(Configuration config) throws Exception {
        return new N3FileRegistry(this);
    }


    @Override
    public void shutdown() {

    }

    public String getSspHostName() {
        return sspHostName;
    }
}
