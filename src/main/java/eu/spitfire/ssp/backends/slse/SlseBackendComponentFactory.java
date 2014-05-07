package eu.spitfire.ssp.backends.slse;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 06.05.14.
 */
public class SlseBackendComponentFactory extends BackendComponentFactory<URI> {

    private SlseAccessor slseAccessor;
    private SlseRegistry slseRegistry;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory}.
     *
     * @param prefix                       the prefix of the backend in the given config (without the ".")
     * @param config                       the SSP config
     * @param localChannel
     * @param internalTasksExecutorService the {@link java.util.concurrent.ScheduledExecutorService} for backend tasks,
     *                                     e.g. translating and forwarding requests to data origins
     * @param ioExecutorService            @throws java.lang.Exception if something went terribly wrong
     */
    public SlseBackendComponentFactory(String prefix, Configuration config, LocalServerChannel localChannel,
                                          ScheduledExecutorService internalTasksExecutorService,
                                          ExecutorService ioExecutorService) throws Exception {

        super(prefix, config, localChannel, internalTasksExecutorService, ioExecutorService);

        this.slseAccessor = new SlseAccessor(this);
        this.slseRegistry = new SlseRegistry(this);
    }


    @Override
    public void initialize() throws Exception {

    }

    @Override
    public DataOriginObserver<URI> getDataOriginObserver(DataOrigin<URI> dataOrigin) {
        return null;
    }

    @Override
    public DataOriginAccessor<URI> getDataOriginAccessor(DataOrigin<URI> dataOrigin) {
        return this.slseAccessor;
    }

    @Override
    public DataOriginRegistry<URI> createDataOriginRegistry(Configuration config) throws Exception {
        return this.slseRegistry;
    }

    @Override
    public void shutdown() {

    }
}
