package eu.spitfire.ssp.backends.n3files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.Observer;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Observer for local n3files
 *
 * @author Oliver Kleine
 */
public class N3FileObserver extends Observer<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private N3FileAccessor n3FileAccessor;


    public N3FileObserver(BackendComponentFactory<Path> componentFactory) {
        super(componentFactory);
        this.n3FileAccessor = ((N3FileBackendComponentFactory) componentFactory).getAccessor(null);
    }


    void deletionDetected(final Path file){

    }


    void updateDetected(final DataOrigin<Path> dataOrigin){
        log.info("File {} was updated!", dataOrigin);

        Futures.addCallback(n3FileAccessor.getStatus(dataOrigin), new FutureCallback<GraphStatusMessage>() {

            @Override
            public void onSuccess(GraphStatusMessage dataOriginStatus) {

                if(dataOriginStatus instanceof ExpiringNamedGraphStatusMessage){
                    ExpiringNamedGraphStatusMessage statusMessage = (ExpiringNamedGraphStatusMessage) dataOriginStatus;

                    Futures.addCallback(updateCache(statusMessage), new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            log.info("Successfully updated cached status from file \"{}\"!",
                                    dataOrigin.getIdentifier());
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.warn("Could not update cached status from file \"{}\"!", dataOrigin.getIdentifier());
                        }
                    });
                }

                else{
                    log.error("Data Origin {} did not return an expiring named graph status but {}",
                            dataOrigin, dataOriginStatus);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("Could not get updated status from file \"{}\"!", throwable);
            }
        });
    }


    @Override
    public void startObservation(DataOrigin<Path> dataOrigin) {
        log.info("Start observation of file {}!", dataOrigin.getIdentifier());
        updateDetected(dataOrigin);
    }
}
