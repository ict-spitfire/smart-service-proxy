package eu.spitfire.ssp.backends.external.turtlefiles;

import eu.spitfire.ssp.backends.generic.Registry;

import java.nio.file.Path;

/**
 * Created by olli on 13.04.14.
 */
public class TurtleFileRegistry extends Registry<Path, TurtleFile> {

    public TurtleFileRegistry(TurtleFileBackendComponentFactory componentFactory) throws Exception {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //Nothing to do...
    }


//    public ListenableFuture<Void> handleN3FileDeletion(final Path file){
//
//        try{
//            if(!file.toString().endsWith(".n3"))
//                throw new IllegalArgumentException("Given file is no N3 file!");
//
//            N3File n3File = new N3File(file, componentFactory.getSspHostName());
//
//            return unregisterDataOrigin(n3File);
//        }
//
//        catch (Exception ex) {
//            SettableFuture<Void> result = SettableFuture.create();
//            result.setException(ex);
//            return result;
//        }
//    }


//    public ListenableFuture<Void> handleN3FileCreation(final Path file){
//
//        final SettableFuture<Void> registrationFuture = SettableFuture.create();
//
//        try {
//            if(!file.toString().endsWith(".n3"))
//                throw new IllegalArgumentException("Given file is no N3 file!");
//
//            log.info("Handle creation of file \"{}\".", file);
//
//            N3FileAccessor n3FileAccessor = (N3FileAccessor) componentFactory.getAccessor(null);
//            final N3File dataOrigin = new N3File(file, componentFactory.getSspHostName());
//
//            ListenableFuture<AccessResult> statusFuture = n3FileAccessor.getStatus(dataOrigin);
//            Futures.addCallback(statusFuture, new FutureCallback<AccessResult>() {
//
//                @Override
//                public void onSuccess(AccessResult dataOriginStatus) {
//                    try{
//                        Futures.addCallback(registerDataOrigin(dataOrigin), new FutureCallback<Void>() {
//
//                            @Override
//                            public void onSuccess(Void aVoid) {
//                                registrationFuture.set(null);
//                            }
//
//                            @Override
//                            public void onFailure(Throwable throwable) {
//                                registrationFuture.setException(throwable);
//                            }
//                        });
//                    }
//
//                    catch(Exception ex){
//                        registrationFuture.setException(ex);
//                    }
//                }
//
//                @Override
//                public void onFailure(Throwable throwable) {
//                    log.error("Could not retrieve status from {}!", file, throwable);
//                    registrationFuture.setException(throwable);
//                }
//            });
//        }
//
//        catch(Exception ex){
//            log.error("This should never happen!", ex);
//            registrationFuture.setException(ex);
//        }
//
//        return registrationFuture;
//    }

}
