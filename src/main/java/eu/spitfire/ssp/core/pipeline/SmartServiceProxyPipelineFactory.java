/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
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
package eu.spitfire.ssp.core.pipeline;

import eu.spitfire.ssp.core.httpServer.*;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;


/**
 * An {@link SmartServiceProxyPipelineFactory} is a factory to generate pipelines to handle imcoming
 * {@link org.jboss.netty.handler.codec.http.HttpRequest}s.
 *
 * @author Oliver Kleine
 *
 */
public class SmartServiceProxyPipelineFactory implements ChannelPipelineFactory {

    private static Logger log = Logger.getLogger(SmartServiceProxyPipelineFactory.class.getName());

    private HttpResponseEncoder httpResponseEncoder;
    private HttpCorsHandler httpCorsHandler;
    private PayloadFormatter payloadFormatter;
    private HttpMirrorUriHandler httpMirrorUriHandler;
    private HttpRequestDispatcher httpRequestDispatcher;
    private ModelCache modelCache;

    ExecutionHandler executionHandler;

    public SmartServiceProxyPipelineFactory(ExecutorService ioExecutorService) throws Exception {
        httpResponseEncoder = new HttpResponseEncoder();
        httpCorsHandler = new HttpCorsHandler();
        payloadFormatter = new PayloadFormatter();
        httpMirrorUriHandler = new HttpMirrorUriHandler();

        executionHandler = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(100, 0, 0));

        modelCache = new ModelCache();
        httpRequestDispatcher = new HttpRequestDispatcher(ioExecutorService);
    }

    public ChannelPipeline getInternalPipeline() throws Exception{
        ChannelPipeline pipeline = Channels.pipeline();

        //pipeline.addLast("Model Cache", modelCache);
        pipeline.addLast("HTTP Request Dispatcher", httpRequestDispatcher);

        return pipeline;
    }

    @Override
	public ChannelPipeline getPipeline() throws Exception {

		ChannelPipeline pipeline = Channels.pipeline();

        //HTTP protocol handlers
		pipeline.addLast("HTTP Decoder", new HttpRequestDecoder());
		pipeline.addLast("HTTP Chunk Aggrgator", new HttpChunkAggregator(1048576));
        pipeline.addLast("HTTP Encoder", new HttpResponseEncoder());
		pipeline.addLast("HTTP Deflater", new HttpContentCompressor());
        pipeline.addLast("Http CORS Handler", new HttpCorsHandler());

        //SSP specific handlers
        //pipeline.addLast("Payload Formatter", new PayloadFormatter());
        //pipeline.addLast("HTTP Mirror URI Handler", new HttpMirrorUriHandler());
        pipeline.addLast("Execution Handler", executionHandler);
        //pipeline.addLast("Model Cache", modelCache);

        pipeline.addLast("HTTP Request Dispatcher", httpRequestDispatcher);

        return pipeline;
	}

}
