package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOriginObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 17:19
 * To change this template use File | Settings | File Templates.
 */
public class FilesObserver extends DataOriginObserver {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public FilesObserver(FilesBackendComponentFactory backendComponentFactory) {
        super(backendComponentFactory);
    }

    public void handleFileModification(Path file){
        log.info("File modified: {}", file);
    }

    public void handleFileDeletion(Path file){
        log.info("File deleted: {}", file);
    }
}
