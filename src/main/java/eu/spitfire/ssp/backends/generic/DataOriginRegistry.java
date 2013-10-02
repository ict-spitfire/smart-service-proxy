package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import eu.spitfire.ssp.backends.generic.messages.*;
import eu.spitfire.ssp.backends.generic.exceptions.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link DataOriginRegistry} is the component to register new data origins, i.e. the resources from data origins.
 * A data origin could e.g. be a Webservice whose response contains the status of at least one semantic resource. In
 * that example the generic type T would be an {@link URI}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOriginRegistry<T> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel localServerChannel;
    private SemanticHttpRequestProcessor httpRequestProcessor;


    protected DataOriginRegistry(BackendComponentFactory<T> backendComponentFactory) {
        this.httpRequestProcessor = backendComponentFactory.getHttpRequestProcessor();
        this.localServerChannel = backendComponentFactory.getLocalServerChannel();
    }

    /**
     * This method is to be called by implementing classes, i.e. registries for particular data origins,
     * to register the model from that data origin at the SSP.
     *
     * @param dataOrigin the data origin of the given model, e.g a file name or Webservice URI
     *
     * @return a {@link ListenableFuture} where {@link ListenableFuture#get()} returns the list of resource proxy URIs
     * for all resources from the given data origin / model.
     */
    protected final ListenableFuture<URI> registerResource(final T dataOrigin, final Model model, final Date expiry){

        final SettableFuture<URI> resourceRegistrationFuture = SettableFuture.create();

        try{
            //Register resource
            final InternalRegisterResourceMessage<T> registerResourceMessage =
                    new InternalRegisterResourceMessage<T>(httpRequestProcessor, dataOrigin, model, expiry){};

            ChannelFuture channelFuture = Channels.write(localServerChannel, registerResourceMessage);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess())
                        resourceRegistrationFuture.set(registerResourceMessage.getResourceUri());
                    else
                        resourceRegistrationFuture.setException(future.getCause());
                }
            });
        }
        catch (URISyntaxException e) {
            log.error("The resource URI was not valid.", e);
            resourceRegistrationFuture.setException(e);
        }
        catch (MultipleSubjectsInModelException e) {
            log.error("There were multiple resources contained in the given model", e);
            resourceRegistrationFuture.setException(e);
        }
        finally{
            return resourceRegistrationFuture;
        }
    }

//    /**
//     * Method to be called by extending classes, i.e. instances of {@link BackendComponentFactory} whenever there is a new
//     * webservice to be created on the smart service proxy, if the network behind this gateway is an IP enabled
//     * network.
//     *
//     * @param resourceUri the (original/remote) {@link URI} of the new resource to be registered.
//     * @param requestProcessor the {@link HttpRequestProcessor} instance to handle incoming requests for that resource
//     *
//     * @return the {@link com.google.common.util.concurrent.SettableFuture <URI>} containing the absolute {@link URI} for the newly registered
//     * service or a {@link Throwable} if an error occurred during the registration process.
//     */
//    private ListenableFuture<URI> registerSemanticResource(URI resourceUri,
//                                                          final SemanticHttpRequestProcessor requestProcessor){
//
//        final SettableFuture<URI> resourceRegistrationFuture = SettableFuture.create();
//
//        //Retrieve the URI the new service is available at on the proxy
//        final SettableFuture<URI> proxyResourceUriFuture =  this.backendResourceManager.retrieveProxyUri(resourceUri);
//
//        proxyResourceUriFuture.addListener(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    //Register new service with the retrieved resource proxy URI
//                    final URI resourceProxyUri = proxyResourceUriFuture.get();
//                    ChannelFuture registrationFuture = Channels.write(localServerChannel,
//                                    new InternalRegisterWebserviceMessage(resourceProxyUri, requestProcessor));
//
//                    registrationFuture.addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
//                            if (channelFuture.isSuccess())
//                                resourceRegistrationFuture.set(resourceProxyUri);
//                            else
//                                resourceRegistrationFuture.setException(channelFuture.getCause());
//                        }
//                    });
//
//                } catch (InterruptedException e) {
//                    log.error("Exception during service registration process.", e);
//                    resourceRegistrationFuture.setException(e);
//
//                } catch (ExecutionException e) {
//                    log.warn("Exception during service registration process.", e.getMessage());
//                    resourceRegistrationFuture.setException(e);
//                }
//
//            }
//        }, this.scheduledExecutorService);
//
//        return resourceRegistrationFuture;
//    }
}
