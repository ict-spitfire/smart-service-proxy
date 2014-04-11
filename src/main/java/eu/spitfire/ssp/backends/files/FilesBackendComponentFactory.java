package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.*;
import eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.apache.commons.configuration.Configuration;

import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 11.04.14.
 */
public class FilesBackendComponentFactory extends BackendComponentFactory<Path> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix          the prefix of the backend in the given config (without the ".")
     * @param config          the SSP config
     * @param executorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks, e.g.
     *                        translating and forwarding requests to data origins
     * @throws Exception if something went terribly wrong
     */
    protected FilesBackendComponentFactory(String prefix, Configuration config,
                                           ScheduledExecutorService executorService) throws Exception {

        super(prefix, config, executorService);
    }

    @Override
    public HttpSemanticProxyWebservice getSemanticProxyWebservice(DataOrigin<Path> dataOrigin) {
        return null;
    }

    @Override
    public DataOriginObserver<Path> getDataOriginObserver(DataOrigin<Path> dataOrigin) {
        return null;
    }

    @Override
    public void initialize() throws Exception {

    }

    @Override
    public DataOriginRegistry<Path> createDataOriginRegistry() {
        return null;
    }

    @Override
    public void shutdown() {

    }
}
