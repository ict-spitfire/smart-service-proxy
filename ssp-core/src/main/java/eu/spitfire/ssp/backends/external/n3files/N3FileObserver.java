package eu.spitfire.ssp.backends.external.n3files;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Observer;

import java.nio.file.Path;

/**
 * Observer for local N3 files
 *
 * @author Oliver Kleine
 */
public class N3FileObserver extends Observer<Path, N3File> {

    public N3FileObserver(BackendComponentFactory<Path, N3File> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startObservation(N3File dataOrigin) {
        //nothing to do (all files in the directory are automatically observed)
    }
}
