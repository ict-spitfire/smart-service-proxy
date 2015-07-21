package eu.spitfire.ssp.backends.external.turtlefiles;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Observer;

import java.nio.file.Path;

/**
 * Observer for local N3 files
 *
 * @author Oliver Kleine
 */
public class TurtleFileObserver extends Observer<Path, TurtleFile> {

    public TurtleFileObserver(BackendComponentFactory<Path, TurtleFile> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startObservation(TurtleFile dataOrigin) {
        //nothing to do (all files in the directory are automatically observed)
    }
}
