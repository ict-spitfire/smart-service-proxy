package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalUpdateResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Extending classes are supposed to observe a data origin of type T. Whenever there was an update detected by
 * the extending observer it must call the method {@link #cacheResourcesStates(Model, Date)}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOriginObserver{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel localServerChannel;
    private ScheduledExecutorService scheduledExecutorService;

    protected DataOriginObserver(BackendComponentFactory backendComponentFactory){
        this.localServerChannel = backendComponentFactory.getLocalServerChannel();
        this.scheduledExecutorService = backendComponentFactory.getScheduledExecutorService();
    }


    protected final void cacheResourcesStates(Model model){
        cacheResourcesStates(model, null);
    }


    /**
     * This method is to be invoked by extending classes if there was an update at the data origin.
     *
     * @param model the {@link Model} containing the new status of the resource(s) hosted at the observed
     *              data origin
     * @param expiry the {@link Date} indicating the expiry of the new status
     */
    protected final void cacheResourcesStates(Model model, final Date expiry){
        final Map<URI, Model> models = ResourceToolbox.getModelsPerSubject(model);
        for(final URI resourceUri : models.keySet()){
            scheduledExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        cacheResourceStatus(models.get(resourceUri), expiry);
                    } catch (MultipleSubjectsInModelException e) {
                        log.error("This should never happen.", e);
                    } catch (URISyntaxException e) {
                        log.error("This should never happen.", e);
                    }
                }
            });
        }
    }


    private ChannelFuture cacheResourceStatus(final Model model, Date expiry)
            throws MultipleSubjectsInModelException, URISyntaxException {

        InternalResourceStatusMessage internalResourceStatusMessage = new InternalResourceStatusMessage(model, expiry);
        return Channels.write(localServerChannel, internalResourceStatusMessage);

    }


    protected ChannelFuture deleteResource(URI resourceUri){
        InternalRemoveResourcesMessage message = new InternalRemoveResourcesMessage(resourceUri);
        return Channels.write(localServerChannel, message);
    }


    protected final void updateResourceStatus(Statement statement, Date expiry){
        InternalUpdateResourceStatusMessage message = new InternalUpdateResourceStatusMessage(statement, expiry);
        Channels.write(localServerChannel, message);
    }



}

