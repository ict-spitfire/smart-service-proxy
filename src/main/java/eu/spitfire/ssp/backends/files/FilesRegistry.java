package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;


/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 17:01
 * To change this template use File | Settings | File Templates.
 */
public class FilesRegistry extends DataOriginRegistry<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    protected FilesRegistry(FilesBackendComponentFactory backendComponentFactory) {
        super(backendComponentFactory);
    }

    public void handleFileCreation(Path file){
        log.info("File created: {}", file);
    }
}
