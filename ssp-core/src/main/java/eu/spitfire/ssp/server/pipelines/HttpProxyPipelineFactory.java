package eu.spitfire.ssp.server.pipelines;

/**
 * Created by olli on 07.07.14.
 */

import eu.spitfire.ssp.server.handler.HttpSemanticPayloadFormatter;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * The {@link eu.spitfire.ssp.server.pipelines.HttpProxyPipelineFactory} is a factory to generate pipelines for channels to handle
 * incoming {@link org.jboss.netty.handler.codec.http.HttpRequest}s.
 *
 * @author Oliver Kleine
 *
 */
public class HttpProxyPipelineFactory implements ChannelPipelineFactory {

    private static Logger log = LoggerFactory.getLogger(HttpProxyPipelineFactory.class.getName());

    private LinkedHashSet<ChannelHandler> handler;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.server.pipelines.HttpProxyPipelineFactory}.
     *
     * @param handler a {@link java.util.LinkedHashSet} containing the handlers to be added to the
     *                pipeline (in most-downstream-first order)
     *
     * @throws Exception if something went terribly wrong
     */
    public HttpProxyPipelineFactory(LinkedHashSet<ChannelHandler> handler) throws Exception {
        this.handler = handler;
    }


    /**
     * The {@link org.jboss.netty.channel.ChannelPipeline} contains the handlers to handle incoming
     * {@link org.jboss.netty.handler.codec.http.HttpRequest}s and send appropriate
     * {@link org.jboss.netty.handler.codec.http.HttpResponse}s.
     *
     * @return the {@link org.jboss.netty.channel.ChannelPipeline} (chain of handlers) to handle incoming
     * {@link org.jboss.netty.handler.codec.http.HttpRequest}s
     *
     * @throws Exception if something went terribly wrong
     */
    @Override
    public ChannelPipeline getPipeline() throws Exception {

        ChannelPipeline pipeline = Channels.pipeline();
        Iterator<ChannelHandler> handlerIterator = handler.iterator();

        //pipeline.addLast("Logging Handler", new DummyHandler());

        //HTTP protocol handlers
        pipeline.addLast("HTTP Decoder", new HttpRequestDecoder());
        pipeline.addLast("HTTP Chunk Aggregator", new HttpChunkAggregator(8388608));
        pipeline.addLast("HTTP Encoder", new HttpResponseEncoder());
        pipeline.addLast("HTTP Deflater", new HttpContentCompressor());

        //SSP specific handlers
        pipeline.addLast("Payload Formatter", new HttpSemanticPayloadFormatter());

        //Execution handler
        ChannelHandler channelHandler = handlerIterator.next();
        pipeline.addLast(channelHandler.getClass().getSimpleName(), channelHandler);
        log.debug("Added {} to pipeline.", channelHandler.getClass().getSimpleName());

        while(handlerIterator.hasNext()){
            channelHandler = handlerIterator.next();
            pipeline.addLast(channelHandler.getClass().getSimpleName(), channelHandler);
            log.debug("Added {} to pipeline.", channelHandler.getClass().getSimpleName());
        }

        return pipeline;
    }
}
