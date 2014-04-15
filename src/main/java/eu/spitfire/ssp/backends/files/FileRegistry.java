package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

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


    public ListenableFuture<Void> handleFileCreation(final Path file){
        log.info("Handle creation of file \"{}\".", file);
        final SettableFuture<Void> registrationFuture = SettableFuture.create();

        try {
            if(file.toString().endsWith(".n3") ){
                FileAccessor fileAccessor = (FileAccessor) componentFactory.getDataOriginAccessor(null);
                ListenableFuture<WrappedDataOriginStatus> statusFuture = fileAccessor.getStatus(file);
                Futures.addCallback(statusFuture, new FutureCallback<WrappedDataOriginStatus>() {

                    @Override
                    public void onSuccess(WrappedDataOriginStatus dataOriginStatus) {
                        FileDataOrigin dataOrigin = new FileDataOrigin(dataOriginStatus.getGraphName(), file);
                        registerDataOrigin(dataOrigin);
                        registrationFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        log.error("Could not retrieve status from {}!", file, throwable);
                        registrationFuture.setException(throwable);
                    }
                });
            }
        }

        catch(Exception ex){
            log.error("This should never happen!", ex);
            registrationFuture.setException(ex);
        }

        return registrationFuture;
    }


//
}
