package eu.spitfire.ssp.gateways;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 16:08
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractResourceObserver {

    public static long MILLIS_PER_YEAR = 31560000000L;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ScheduledExecutorService scheduledExecutorService;
    private LocalServerChannel localChannel;

    public AbstractResourceObserver(ScheduledExecutorService scheduledExecutorService, LocalServerChannel localChannel){
        this.scheduledExecutorService = scheduledExecutorService;
        this.localChannel = localChannel;
    }

    protected ScheduledExecutorService getScheduledExecutorService(){
        return this.scheduledExecutorService;
    }

    public ChannelFuture cacheResourceStatus(URI resourceUri, Model resourceStatus){
        return this.cacheResourceStatus(resourceUri, resourceStatus,
                new Date(System.currentTimeMillis() + MILLIS_PER_YEAR));
    }

    public ChannelFuture cacheResourceStatus(URI resourceUri, Model resourceStatus, Date expiry){

        final ResourceStatusMessage resourceStatusMessage =
                new ResourceStatusMessage(resourceUri, resourceStatus, expiry);

        ChannelFuture channelFuture = Channels.write(localChannel, resourceStatusMessage);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess())
                    log.debug("Succesfully updated resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
                else
                    log.warn("Failed to update resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
            }
        });

        return channelFuture;
    }

    public ChannelFuture removeResourceStatusFromCache(URI resourceUri){
        final InternalRemoveResourceMessage removeResourceMessage = new InternalRemoveResourceMessage(resourceUri);
        ChannelFuture channelFuture = Channels.write(localChannel, removeResourceMessage);

        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess())
                    log.debug("Succesfully removed resource {}.", removeResourceMessage.getResourceUri());
                else
                    log.warn("Failed to remove resource {}.", removeResourceMessage.getResourceUri());
            }
        });

        return channelFuture;
    }
}
