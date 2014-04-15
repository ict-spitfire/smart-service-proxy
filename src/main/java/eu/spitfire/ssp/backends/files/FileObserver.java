package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.WatchKey;

/**
 * Created by olli on 15.04.14.
 */
public class FileObserver extends DataOriginObserver<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private FileAccessor fileAccessor;


    public FileObserver(BackendComponentFactory<Path> componentFactory) {
        super(componentFactory);
        this.fileAccessor = ((FilesBackendComponentFactory) componentFactory).getDataOriginAccessor(null);
    }


    void updateDetected(Path file){
        log.info("File {} was updated!", file);
    }

    @Override
    public void startObservation(DataOrigin<Path> dataOrigin) {
        log.info("Start observation of file {}!", dataOrigin.getIdentifier());
    }
}
