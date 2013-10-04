package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 17:01
 * To change this template use File | Settings | File Templates.
 */
public class FilesBackendComponentFactory extends BackendComponentFactory<Path>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private HttpRequestProcessorForFiles httpRequestProcessor;

    private WatchService watchService;
    private Path directory;
    private FilesObserver filesObserver;
    private FilesWatcher filesWatcher;
    public FilesBackendComponentFactory(String prefix, LocalPipelineFactory localPipelineFactory,
                                        ScheduledExecutorService scheduledExecutorService, String sspHostName,
                                        int sspHttpPort, Path directory)
            throws Exception {

        super(prefix, localPipelineFactory, scheduledExecutorService, sspHostName, sspHttpPort);

        this.directory = directory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.filesObserver = new FilesObserver(this);
    }

    public WatchService getWatchService(){
        return this.watchService;
    }

    public FilesObserver getFilesObserver(){
        return this.filesObserver;
    }

    @Override
    public SemanticHttpRequestProcessor getHttpRequestProcessor() {
        return null;
    }

    @Override
    public void initialize() throws Exception{
        this.filesWatcher = new FilesWatcher(this);
        this.filesWatcher.initialize(directory);
    }

    @Override
    public DataOriginRegistry<Path> createDataOriginRegistry() {
        log.info("Create Files Registry");
        return new FilesRegistry(this);
    }

    @Override
    public void shutdown() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


}
