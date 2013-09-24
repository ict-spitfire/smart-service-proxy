/**
 * Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire.ssp.server.pipeline;

import eu.spitfire.ssp.server.pipeline.handler.*;
import eu.spitfire.ssp.server.pipeline.handler.cache.SemanticCache;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;


/**
 * The {@link SmartServiceProxyPipelineFactory} is a factory to generate pipelines to handle messages (internal
 * or incoming)
 *
 * @author Oliver Kleine
 *
 */
public class SmartServiceProxyPipelineFactory implements ChannelPipelineFactory {

    private static Logger log = LoggerFactory.getLogger(SmartServiceProxyPipelineFactory.class.getName());

    private HttpRequestDispatcher httpRequestDispatcher;
    private SemanticCache semanticCache            ;
    private ExecutionHandler executionHandler;

    public SmartServiceProxyPipelineFactory(ExecutorService ioExecutorService, SemanticCache cache,
                                            HttpRequestDispatcher httpRequestDispatcher) throws Exception {

        executionHandler = new ExecutionHandler(ioExecutorService);
        semanticCache = cache;

        log.info("Added instance of {} as cache.", semanticCache.getClass().getName());

        this.httpRequestDispatcher = httpRequestDispatcher;
    }

//    /**
//     * The internal pipeline contains the handlers to handle internal messages for e.g. resource registration and
//     * resource status updates.
//     *
//     * @return the pipeline (chain of handlers) for internal messages.
//     *
//     * @throws Exception if some unexpected error occurred
//     */
//    public ChannelPipeline getInternalPipeline() throws Exception{
//        ChannelPipeline pipeline = Channels.pipeline();
//
//        //pipeline.addLast("Internal Sink", internalPipelineSink);
//        pipeline.addLast("Semantic Cache", semanticCache);
//        pipeline.addLast("HTTP Request Dispatcher", httpRequestDispatcher);
//
//        return pipeline;
//    }

    /**
     * The pipeline contains the handlers to handle incoming HTTP requests and return a proper HTTP response
     *
     * @return the pipeline (chain of handlers) to handle incoming HTTP requests
     *
     * @throws Exception if some unexpected error occured
     */
    @Override
	public ChannelPipeline getPipeline() throws Exception {

		ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("Execution Handler", executionHandler);

        //HTTP protocol handlers
		pipeline.addLast("HTTP Decoder", new HttpRequestDecoder());
		pipeline.addLast("HTTP Chunk Aggrgator", new HttpChunkAggregator(1048576));
        pipeline.addLast("HTTP Encoder", new HttpResponseEncoder());
		pipeline.addLast("HTTP Deflater", new HttpContentCompressor());

        //SSP specific handlers
        pipeline.addLast("Payload Formatter", new SemanticPayloadFormatter());
        pipeline.addLast("Semantic Cache", semanticCache);
        pipeline.addLast("HTTP Request Dispatcher", httpRequestDispatcher);

        return pipeline;
	}
}
