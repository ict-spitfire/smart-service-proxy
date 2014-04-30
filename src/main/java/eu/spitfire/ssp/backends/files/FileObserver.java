package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Observer for local files
 */
public class FileObserver extends DataOriginObserver<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private FileAccessor fileAccessor;


    public FileObserver(BackendComponentFactory<Path> componentFactory) {
        super(componentFactory);
        this.fileAccessor = ((FilesBackendComponentFactory) componentFactory).getDataOriginAccessor(null);
    }


    void deletionDetected(final Path file){

    }


    void updateDetected(final Path file){
        log.info("File {} was updated!", file);

        Futures.addCallback(fileAccessor.getStatus(file), new FutureCallback<GraphStatusMessage>() {

            @Override
            public void onSuccess(GraphStatusMessage dataOriginStatus) {

                if(dataOriginStatus instanceof ExpiringNamedGraphStatusMessage){
                    ExpiringNamedGraphStatusMessage statusMessage = (ExpiringNamedGraphStatusMessage) dataOriginStatus;

                    Futures.addCallback(updateCache(statusMessage), new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            log.info("Successfully updated cached status from file \"{}\"!", file);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.warn("Could not update cached status from file \"{}\"!", file);
                        }
                    });
                }

                else{
                    log.error("Data Origin {} did not return an expiring named graph status but {}",
                            file, dataOriginStatus);
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
        updateDetected(dataOrigin.getIdentifier());
    }
}
