package eu.spitfire.ssp.backends;

import com.hp.hpl.jena.rdf.model.Resource;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 16:23
 * To change this template use File | Settings | File Templates.
 */
public abstract class ResourceStatusHandler<T> {

    //public static long MILLIS_PER_YEAR = 31560000000L;
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    protected BackendComponentFactory<T> backendComponentFactory;

    protected ResourceStatusHandler(BackendComponentFactory<T> backendComponentFactory){
        this.backendComponentFactory = backendComponentFactory;
    }

    protected ChannelFuture cacheResourceStatus(Resource resource, Date expiry){

        final ResourceStatusMessage resourceStatusMessage = new ResourceStatusMessage(resource, expiry);

        ChannelFuture channelFuture = Channels.write(backendComponentFactory.getLocalServerChannel(),
                resourceStatusMessage);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess())
                    log.debug("Succesfully cached status of resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
                else
                    log.warn("Failed to cache status of resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
            }
        });

        return channelFuture;
    }

    protected void removeAllResources(final T dataOrigin){
        backendComponentFactory.getScheduledExecutorService().schedule(new Runnable(){
            @Override
            public void run() {
                log.warn("Remove all resources from data origin {}.", dataOrigin);
                Object[] tmp = backendComponentFactory.getResources(dataOrigin).toArray();
                URI[] resourceUris = Arrays.copyOf(tmp, tmp.length, URI[].class);

                for(URI resourceUri : resourceUris){
                    final InternalRemoveResourcesMessage removeResourceMessage = new InternalRemoveResourcesMessage(resourceUri);
                    Channels.write(backendComponentFactory.getLocalServerChannel(), removeResourceMessage);
                }
            }
        }, 0, TimeUnit.SECONDS) ;
    }
}
