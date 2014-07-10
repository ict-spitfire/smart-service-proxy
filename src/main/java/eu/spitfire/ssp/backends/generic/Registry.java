package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.internal.messages.requests.DataOriginDeregistration;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.server.internal.messages.requests.DataOriginRegistration;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * A {@link Registry} is the component to register new data origins, i.e. the resources from data origins.
 * A data origin could e.g. be a Webservice whose response contains the status of at least one semantic resource. In
 * that example the generic type T would be an {@link java.net.URI}.
 *
 * @author Oliver Kleine
 */
public abstract class Registry<I, D extends DataOrigin<I>> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected BackendComponentFactory<I, D> componentFactory;

    protected Registry(BackendComponentFactory<I, D> componentFactory) {
        this.componentFactory = componentFactory;
    }


    public final ListenableFuture<Void> registerDataOrigin(final D dataOrigin, ExpiringNamedGraph initialStatus){
        final SettableFuture<Void> registrationFuture = SettableFuture.create();
        registerDataOrigin(dataOrigin, initialStatus.getGraph(), initialStatus.getExpiry(), registrationFuture);
        return registrationFuture;
    }

    /**
     * This method is to be called by implementing classes, i.e. registries for particular data origins,
     * to register the model from that data origin at the SSP.
     *
     * @param dataOrigin the data origin to be registered
     *
     * @return a {@link ListenableFuture} where {@link ListenableFuture#get()} returns the list of resource proxy URIs
     * for all resources from the given data origin / model.
     */
    public final ListenableFuture<Void> registerDataOrigin(final D dataOrigin){

        final SettableFuture<Void> registrationFuture = SettableFuture.create();

        //retrieve initial status
        Accessor<I, D> accessor = componentFactory.getAccessor(dataOrigin);
        ListenableFuture<? extends DataOriginInquiryResult> accessResultFuture = accessor.getStatus(dataOrigin);

        //Await the initial status retrieval and perform the actual registration
        Futures.addCallback(accessResultFuture, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object accessResult) {
                if(accessResult == null || !(accessResult instanceof ExpiringNamedGraph)){
                    String message = String.format("Unexpected access result for data origin \"%s\": %s",
                            dataOrigin.getIdentifier().toString(), accessResult == null ? null : accessResult.toString()
                    );

                    log.error(message);
                    registrationFuture.setException(new Exception(message));
                    return;
                }

                ExpiringNamedGraph expiringNamedGraph = (ExpiringNamedGraph) accessResult;
                registerDataOrigin(
                        dataOrigin, expiringNamedGraph.getGraph(), expiringNamedGraph.getExpiry(), registrationFuture
                );
            }

            @Override
            public void onFailure(Throwable t) {
                registrationFuture.setException(t);
            }
        });

     return registrationFuture;
    }


    private void registerDataOrigin(final D dataOrigin, Model initialStatus, Date expiry,
                                    final SettableFuture<Void> registrationFuture){

        log.debug("Try to register data origin with identifier \"{}\".", dataOrigin.getIdentifier());

        try{
            //Create registration message
            DataOriginRegistration<I, D> registration = new DataOriginRegistration<>(
                    dataOrigin, initialStatus, expiry, componentFactory.getDataOriginMapper(), registrationFuture
            );

            //Send registration message
            ChannelFuture channelFuture = Channels.write(componentFactory.getLocalChannel(), registration);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(!future.isSuccess()){
                        log.error("Exception on ChannelFuture during registration of graph {}.",
                                dataOrigin.getGraphName(), future.getCause());

                        registrationFuture.setException(future.getCause());
                    }
                }
            });


            //Await registration result
            Futures.addCallback(registrationFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    log.info("Successfully registered new data origin \"{}\".", dataOrigin);

                    //Start observation if the data origin is observable
                    if(dataOrigin.isObservable()){
                        Observer<I, D> observer = componentFactory.getObserver(dataOrigin);

                        if(observer != null){
                            log.info("Start observation of data origin \"{}\".", dataOrigin);
                            observer.startObservation(dataOrigin);
                        }
                        else{
                            log.warn("Backend component factory did not return a data origin observer for \"{}\"",
                                    dataOrigin.getIdentifier());
                        }
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("Exception during registration of graph {}.", dataOrigin.getGraphName(), t.getMessage());
                    log.debug("Exception during registration of graph {}", dataOrigin.getGraphName(), t);
                }
            });
        }

        catch (Exception ex) {
            log.error("Registration of data origin {} failed!", dataOrigin.getIdentifier(), ex);
            registrationFuture.setException(ex);
        }
    }


    public ListenableFuture<Void> unregisterDataOrigin(final I identifier){
        final SettableFuture<Void> unregistrationFuture = SettableFuture.create();
        log.info("Try to unregister data origin: \"{}\".", identifier);

        DataOriginMapper<I, D> dataOriginMapper = this.componentFactory.getDataOriginMapper();
        final D dataOrigin = dataOriginMapper.getDataOrigin(identifier);

        //If no such data origin exists
        if(dataOrigin == null){
            unregistrationFuture.setException(
                    new Exception("No data origin for identifier " + identifier.toString() + " found")
            );

            return unregistrationFuture;
        }

        //Handle unregistration
        DataOriginDeregistration<I, D> unregistration = new DataOriginDeregistration<>(dataOrigin, unregistrationFuture);

        //Send the unregistration message
        ChannelFuture channelFuture = Channels.write(componentFactory.getLocalChannel(), unregistration);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    unregistrationFuture.setException(future.getCause());
                }
            }
        });

        //Await the unregistration result
        Futures.addCallback(unregistrationFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                log.info("Succesfully unregistered data origin \"{}\"", dataOrigin);
            }

            @Override
            public void onFailure(Throwable t) {
                log.info("Exception during unregistration of data origin \"{}\"!", dataOrigin, t);
            }
        });

        return unregistrationFuture;
    }

    /**
     * This method is automatically invoked by the framework. Implementing classes are supposed to do everything that
     * is necessary to enable new data origins to register at this
     * {@link Registry} instance.
     */
    public abstract void startRegistry() throws Exception;
}
