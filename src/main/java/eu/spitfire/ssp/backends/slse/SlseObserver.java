package eu.spitfire.ssp.backends.slse;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by olli on 20.06.14.
 */
public class SlseObserver extends DataOriginObserver<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ScheduledExecutorService executorService;

    protected SlseObserver(BackendComponentFactory<URI> componentFactory) {
        super(componentFactory);
        this.executorService = componentFactory.getInternalTasksExecutorService();
    }

    @Override
    public void startObservation(final DataOrigin<URI> dataOrigin) {
        this.executorService.scheduleAtFixedRate(new SlseObservation((SlseDataOrigin) dataOrigin),
                0, 30, TimeUnit.SECONDS);
    }

    private class SlseObservation implements Runnable{

        private SlseDataOrigin dataOrigin;

        private SlseObservation(SlseDataOrigin dataOrigin){
            this.dataOrigin = dataOrigin;
        }

        @Override
        public void run() {
            try{
                DataOriginAccessor<URI> accessor = componentFactory.getDataOriginAccessor(dataOrigin);

                Futures.addCallback(accessor.getStatus(dataOrigin), new FutureCallback<GraphStatusMessage>() {
                    @Override
                    public void onSuccess(@Nullable GraphStatusMessage graphStatusMessage) {
                        if(graphStatusMessage == null){
                            log.error("Status of graph {} was NULL...");
                            return;
                        }

                        ExpiringNamedGraphStatusMessage message = (ExpiringNamedGraphStatusMessage) graphStatusMessage;

                        Futures.addCallback(SlseObserver.this.updateCache(message), new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void result) {
                                log.info("Successfully updated cached status of graph {}", dataOrigin.getGraphName());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.error("Failed to update cached status of graph {}", dataOrigin.getGraphName());
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Failed to retrieve status of graph {} from {}",
                                new Object[]{dataOrigin.getGraphName(), dataOrigin.getIdentifier(), t});
                    }
                });
            }
            catch(Exception ex){
                log.error("Could not start observation of graph {}", dataOrigin.getGraphName(), ex);
            }
        }
    }
}
