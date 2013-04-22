package eu.spitfire_project.smart_service_proxy.backends.coap;

import eu.spitfire_project.smart_service_proxy.core.httpServer.ModelCache;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;

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

        pipeline.addLast("Logging Sink", new SimpleChannelDownstreamHandler(){

            private Logger log = Logger.getLogger(this.getClass().getName());

            @Override
            public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
                log.error("Unexcepctedly received message of type " + me.getMessage().getClass().getName());
            }
        });

        pipeline.addLast("Cache", ModelCache.getInstance());
        pipeline.addLast("CoapBackend", coapBackend);
        return pipeline;
    }
}
