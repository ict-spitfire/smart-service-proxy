package eu.spitfire.ssp.proxyservicemanagement;

import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 16:08
 * To change this template use File | Settings | File Templates.
 */
public class AbstractResourceObserver {

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

    public void updateResourceStatus(final ResourceStatusMessage resourceStatusMessage){
        ChannelFuture future = Channels.write(localChannel, resourceStatusMessage);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    log.debug("Succesfully updated resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
                else
                    log.warn("Failed to update resource {} (expiry: {}).",
                            resourceStatusMessage.getResourceUri(), resourceStatusMessage.getExpiry());
            }
        });
    }

    public void removeResource(final InternalRemoveResourceMessage removeResourceMessage){
        ChannelFuture future = Channels.write(localChannel, removeResourceMessage);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    log.debug("Succesfully removed resource {}.", removeResourceMessage.getResourceUri());
                else
                    log.warn("Failed to remove resource {}.", removeResourceMessage.getResourceUri());
            }
        });
    }
}
