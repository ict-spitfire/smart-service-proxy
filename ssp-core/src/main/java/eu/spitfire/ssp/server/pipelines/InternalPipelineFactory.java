package eu.spitfire.ssp.server.pipelines;

import eu.spitfire.ssp.server.handler.InternalChannelSink;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;

/**
 * Factory class to create the channels for internal message, e.g. for resource registration or resource status
 * update. The framework automatically provides one seperate channel per backend.
 *
 * @author Oliver Kleine
 */
public class InternalPipelineFactory implements ChannelPipelineFactory {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LinkedHashSet<ChannelHandler> handler;
    private InternalChannelSink internalChannelSink;

    public InternalPipelineFactory(LinkedHashSet<ChannelHandler> handler){
        this.handler = handler;
        this.internalChannelSink = new InternalChannelSink();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("Internal Pipeline Sink", internalChannelSink);

        for (ChannelHandler channelHandler : handler) {
            pipeline.addLast(channelHandler.getClass().getSimpleName(), channelHandler);
            log.info("Added {} to internal pipeline", channelHandler.getClass().getSimpleName());
        }

        return pipeline;
    }
}
