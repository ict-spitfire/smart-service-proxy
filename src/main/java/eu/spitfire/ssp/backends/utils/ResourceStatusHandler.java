package eu.spitfire.ssp.backends.utils;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 16:23
 * To change this template use File | Settings | File Templates.
 */
public abstract class ResourceStatusHandler {

    public static long MILLIS_PER_YEAR = 31560000000L;

    //protected LocalServerChannel localServerChannel;
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    protected BackendManager backendManager;


    protected ResourceStatusHandler(BackendManager backendManager){
        this.backendManager = backendManager;
    }

    protected ChannelFuture cacheResourceStatus(URI resourceUri, Model resourceStatus){
        return this.cacheResourceStatus(resourceUri, resourceStatus,
                new Date(System.currentTimeMillis() + MILLIS_PER_YEAR));
    }

    protected ChannelFuture cacheResourceStatus(URI resourceUri, Model resourceStatus, Date expiry){

        final ResourceResponseMessage resourceResponseMessage =
                new ResourceResponseMessage(resourceStatus.getResource(resourceUri.toString()), expiry);

        ChannelFuture channelFuture = Channels.write(backendManager.getLocalServerChannel(), resourceResponseMessage);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess())
                    log.debug("Succesfully cached status of resource {} (expiry: {}).",
                            resourceResponseMessage.getResourceUri(), resourceResponseMessage.getExpiry());
                else
                    log.warn("Failed to cache status of resource {} (expiry: {}).",
                            resourceResponseMessage.getResourceUri(), resourceResponseMessage.getExpiry());
            }
        });

        return channelFuture;
    }

    protected ChannelFuture removeResourceStatusFromCache(URI resourceUri){
        final InternalRemoveResourcesMessage removeResourceMessage = new InternalRemoveResourcesMessage(resourceUri);
        ChannelFuture channelFuture = Channels.write(backendManager.getLocalServerChannel(), removeResourceMessage);

//        channelFuture.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture channelFuture) throws Exception {
//                if (channelFuture.isSuccess())
//                    log.debug("Succesfully removed resource from cache{}.", removeResourceMessage.getResourceUri());
//                else
//                    log.warn("Failed to remove resource {}.", removeResourceMessage.getResourceUri());
//            }
//        });

        return channelFuture;
    }
}
