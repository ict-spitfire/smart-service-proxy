package eu.spitfire.ssp.backends.internal.se;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntityRegistry extends Registry<URI, SemanticEntity>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    protected SemanticEntityRegistry(BackendComponentFactory<URI, SemanticEntity> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //TODO register SE creation services
    }
}
