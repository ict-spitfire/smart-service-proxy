package eu.spitfire.ssp.backends;

import eu.spitfire.ssp.server.pipeline.handler.HttpRequestDispatcher;
import eu.spitfire.ssp.server.pipeline.handler.InternalPipelineSink;
import eu.spitfire.ssp.server.pipeline.handler.cache.SemanticCache;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

/**
 * Factory class to create the pipeline for internal messages, e.g. for resource registration or resource status
 * update. The framework automatically provides one seperate channel per backend.
 *
 * @author Oliver Kleine
 */
public class LocalPipelineFactory implements ChannelPipelineFactory {

    private final HttpRequestDispatcher httpRequestDispatcher;
    private final SemanticCache semanticCache;
    private final InternalPipelineSink internalPipelineSink;

    public LocalPipelineFactory(HttpRequestDispatcher httpRequestDispatcher,  SemanticCache semanticCache){

        this.httpRequestDispatcher = httpRequestDispatcher;
        this.semanticCache = semanticCache;

        this.internalPipelineSink = new InternalPipelineSink();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("Internal Sink", internalPipelineSink);
        pipeline.addLast("Semantic Cache", semanticCache);
        pipeline.addLast("HTTP Request Dispatcher", httpRequestDispatcher);

        return pipeline;
    }
}
