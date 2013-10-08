package eu.spitfire.ssp.server.channels;

import eu.spitfire.ssp.server.channels.handler.InternalPipelineSink;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Factory class to create the channels for internal messages, e.g. for resource registration or resource status
 * update. The framework automatically provides one seperate channel per backend.
 *
 * @author Oliver Kleine
 */
public class LocalPipelineFactory implements ChannelPipelineFactory {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LinkedHashSet<ChannelHandler> handler;
    private InternalPipelineSink internalPipelineSink;

    public LocalPipelineFactory(LinkedHashSet<ChannelHandler> handler){
        this.handler = handler;
        this.internalPipelineSink = new InternalPipelineSink();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("Internal Pipeline Sink", internalPipelineSink);
        Iterator<ChannelHandler> handlerIterator = handler.iterator();
        while(handlerIterator.hasNext()){
            ChannelHandler channelHandler = handlerIterator.next();
            pipeline.addLast(channelHandler.getClass().getSimpleName(), channelHandler);
            log.info("Added {} to internal pipeline", channelHandler.getClass().getSimpleName());
        }

        return pipeline;
    }
}
