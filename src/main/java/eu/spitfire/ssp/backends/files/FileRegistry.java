package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.ExpiringNamedGraph;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Created by olli on 13.04.14.
 */
public class FileRegistry extends DataOriginRegistry<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    protected FileRegistry(FilesBackendComponentFactory componentFactory) throws Exception {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //Nothing to do...
    }


    public ListenableFuture<Void> handleN3FileDeletion(final Path file){

        try{
            if(!file.toString().endsWith(".n3"))
                throw new IllegalArgumentException("Given file is no N3 file!");

            FileDataOrigin fileDataOrigin = new FileDataOrigin(file,
                    ((FilesBackendComponentFactory) componentFactory).getSspHostName());

            return removeDataOrigin(fileDataOrigin);
        }

        catch (Exception ex) {
            SettableFuture<Void> result = SettableFuture.create();
            result.setException(ex);
            return result;
        }
    }


    public ListenableFuture<Void> handleN3FileCreation(final Path file){

        final SettableFuture<Void> registrationFuture = SettableFuture.create();

        try {
            if(!file.toString().endsWith(".n3"))
                throw new IllegalArgumentException("Given file is no N3 file!");

            log.info("Handle creation of file \"{}\".", file);

            FileAccessor fileAccessor = (FileAccessor) componentFactory.getDataOriginAccessor(null);
            ListenableFuture<ExpiringNamedGraph> statusFuture = fileAccessor.getStatus(file);
            Futures.addCallback(statusFuture, new FutureCallback<ExpiringNamedGraph>() {

                @Override
                public void onSuccess(ExpiringNamedGraph dataOriginStatus) {
                    try{
                        FileDataOrigin dataOrigin = new FileDataOrigin(file,
                                ((FilesBackendComponentFactory) componentFactory).getSspHostName());

                        Futures.addCallback(registerDataOrigin(dataOrigin), new FutureCallback<Void>() {

                            @Override
                            public void onSuccess(Void aVoid) {
                                registrationFuture.set(null);
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                registrationFuture.setException(throwable);
                            }
                        });
                    }

                    catch(Exception ex){
                        registrationFuture.setException(ex);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.error("Could not retrieve status from {}!", file, throwable);
                    registrationFuture.setException(throwable);
                }
            });
        }

        catch(Exception ex){
            log.error("This should never happen!", ex);
            registrationFuture.setException(ex);
        }

        return registrationFuture;
    }

}
