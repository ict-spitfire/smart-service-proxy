package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
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
import java.util.concurrent.TimeUnit;

/**
 * Extending classes are supposed to observe a data origin of type T. Whenever there was an update detected by
 * the extending observer it must call the method {@link #updateResourcesStates(Model, Date)}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOriginObserver {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel localServerChannel;
    private ScheduledExecutorService scheduledExecutorService;

    protected DataOriginObserver(BackendComponentFactory backendComponentFactory){
        this.localServerChannel = backendComponentFactory.getLocalServerChannel();
        this.scheduledExecutorService = backendComponentFactory.getScheduledExecutorService();
    }


    /**
     * This method is to be invoked by extending classes if there was an update at the data origin.
     *
     * @param model the {@link Model} containing the new status of the resource(s) hosted at the observed
     *              data origin
     * @param expiry the {@link Date} indicating the expiry of the new status
     */
    protected final void updateResourcesStates(Model model, final Date expiry){
        final Map<URI, Model> models = ResourceToolbox.getModelsPerSubject(model);
        for(final URI resourceUri : models.keySet()){
            scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        updateResourceStatus(models.get(resourceUri), expiry);
                    } catch (MultipleSubjectsInModelException e) {
                        log.error("This should never happen.", e);
                    } catch (URISyntaxException e) {
                        log.error("This should never happen.", e);
                    }
                }
            }, 0, TimeUnit.MILLISECONDS);
        }
    }



    private ChannelFuture updateResourceStatus(final Model model, Date expiry)
                                                        throws MultipleSubjectsInModelException, URISyntaxException {

        InternalResourceStatusMessage internalResourceStatusMessage = new InternalResourceStatusMessage(model, expiry);
        return Channels.write(localServerChannel, internalResourceStatusMessage);
    }
}

