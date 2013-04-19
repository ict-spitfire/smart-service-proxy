package eu.spitfire_project.smart_service_proxy.backends.coap;

import eu.spitfire_project.smart_service_proxy.core.httpServer.ModelCache;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

/**
 * Created with IntelliJ IDEA.
 * User: spitfire
 * Date: 4/18/13
 * Time: 5:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class ResourceUpdateChannelPipelineFactory implements ChannelPipelineFactory{

    private CoapBackend coapBackend;

    public ResourceUpdateChannelPipelineFactory(CoapBackend coapBackend){
        this.coapBackend = coapBackend;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("Cache", ModelCache.getInstance());
        pipeline.addLast("CoapBackend", coapBackend);

        return pipeline;
    }
}
