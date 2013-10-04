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

            log.info("Try to register resource {} from data origin {}",
                    registerResourceMessage.getResourceUri(), dataOrigin);

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
}
