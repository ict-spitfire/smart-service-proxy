package eu.spitfire.ssp.backend.files;

import eu.spitfire.ssp.backend.generic.BackendComponentFactory;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;

import java.nio.file.Path;

/**
 * Observer for local N3 files
 *
 * @author Oliver Kleine
 */
public class RdfFilesObserver extends DataOriginObserver<Path, RdfFile> {

    public RdfFilesObserver(BackendComponentFactory<Path, RdfFile> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startObservation(RdfFile dataOrigin) {
        //nothing to do (all files in the directory are automatically observed by the registry...)
    }
}
