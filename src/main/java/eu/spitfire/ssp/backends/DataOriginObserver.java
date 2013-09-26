package eu.spitfire.ssp.backends;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourcesMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 16:08
 * To change this template use File | Settings | File Templates.
 */
public abstract class DataOriginObserver<T> extends ResourceStatusHandler<T> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private T dataOrigin;
    private ScheduledFuture timeoutFuture = null;

    protected DataOriginObserver(BackendComponentFactory<T> backendComponentFactory, T dataOrigin){
        super(backendComponentFactory);
        this.dataOrigin = dataOrigin;
    }

    @Override
    protected ChannelFuture cacheResourceStatus(final Resource resource, Date expiry){
        //Cancel to let the observation of the resource fail
        log.info("Timeout Future is null: {}", timeoutFuture == null);
        if(timeoutFuture != null){
            if(timeoutFuture.cancel(false))
                log.info("Received update notification for resource {} from {}. Timeout canceled.",
                        resource.getURI(), dataOrigin);
            else
                log.warn("Received update notification for resource {} from {}. But timeout could not be canceled.",
                        resource, dataOrigin);
        }

        //Schedule new fail of observation
        log.info("Schedule observation timeout at {}", expiry);
        timeoutFuture = this.backendComponentFactory.getScheduledExecutorService().schedule(new Runnable(){
            @Override
            public void run() {
                handleObservationTimeout(dataOrigin);
            }
        }, expiry.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        return super.cacheResourceStatus(resource, expiry);
    }

    /**
     * Returns the data origin this observer is observing
     * @return the data origin this observer is observing
     */
    public T getDataOrigin() {
        return dataOrigin;
    }

    /**
     * This method is invoked by the Smart Service Proxy if the observation of a data origin timed out, i.e. there was
     * no update notification received before the expiry of the previous update notification.
     *
     * @param dataOrigin The name of the resource whose observation timed out
     */
    public abstract void handleObservationTimeout(T dataOrigin);

}

