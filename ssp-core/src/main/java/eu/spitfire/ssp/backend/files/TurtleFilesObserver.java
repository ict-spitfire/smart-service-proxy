package eu.spitfire.ssp.backend.files;

import eu.spitfire.ssp.backend.generic.ComponentFactory;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;

import java.nio.file.Path;

/**
 * Observer for local N3 files
 *
 * @author Oliver Kleine
 */
public class TurtleFilesObserver extends DataOriginObserver<Path, TurtleFile> {

    public TurtleFilesObserver(ComponentFactory<Path, TurtleFile> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startObservation(TurtleFile dataOrigin) {
        //nothing to do (all files in the directory are automatically observed by the registry...)
    }
}
