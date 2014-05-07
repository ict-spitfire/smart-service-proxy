package eu.spitfire.ssp.backends.files;

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
public class FilesBackendComponentFactory extends BackendComponentFactory<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private FileAccessor fileAccessor;
    private FileObserver fileObserver;
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
    public FilesBackendComponentFactory(String prefix, Configuration config, LocalServerChannel localChannel,
            ScheduledExecutorService backendTasksExecutorService, ExecutorService ioExecutorService) throws Exception {

        super(prefix, config, localChannel, backendTasksExecutorService, ioExecutorService);
        this.sspHostName = config.getString("SSP_HOST_NAME");
        this.config = config.subset(prefix);

    }


    @Override
    public void initialize() throws Exception{
        this.watchService = FileSystems.getDefault().newWatchService();

        this.fileAccessor = new FileAccessor(this);
        this.fileObserver = new FileObserver(this);

        FileWatcher fileWatcher = new FileWatcher(this);
        fileWatcher.startFileWatching();

    }


    Configuration getConfig(){
        return this.config;
    }

    @Override
    public FileObserver getDataOriginObserver(DataOrigin<Path> dataOrigin) {
//        Path identifier = dataOrigin.getIdentifier();
//        Path directory = identifier.getParent();
//
//        if(!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)){
//            log.error("This should never happen... Directory path {} is no directory!", directory);
//            return null;
//        }
//
//        FileObserver fileObserver = fileObservers.get(directory);
//
//        if(fileObserver == null){
//
//            fileObserver = new FileObserver(this);
//            this.fileObservers.put(directory, fileObserver);
//        }

        return this.fileObserver;

    }


    @Override
    public FileAccessor getDataOriginAccessor(DataOrigin<Path> dataOrigin) {
        return this.fileAccessor;
    }


    @Override
    public FileRegistry createDataOriginRegistry(Configuration config) throws Exception {
        return new FileRegistry(this);
    }


    public WatchService getWatchService(){
        return this.watchService;
    }

    @Override
    public void shutdown() {

    }

    public String getSspHostName() {
        return sspHostName;
    }
}
