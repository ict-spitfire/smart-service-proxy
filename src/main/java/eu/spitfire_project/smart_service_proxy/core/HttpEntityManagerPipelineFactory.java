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
package eu.spitfire_project.smart_service_proxy.core;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;


/**
 * An {@link HttpEntityManagerPipelineFactory} is a factory to generate pipelines to handle imcoming HTTP requests which have
 * an {@link EntityManager} at the topmost position of the pipeline. The {@link EntityManager} handles the incoming
 * {@link org.jboss.netty.handler.codec.http.HttpRequest} and produces a
 * {@link org.jboss.netty.handler.codec.http.HttpResponse} to send write it on the downstream
 *	
 * @author Oliver Kleine
 *
 */
public class HttpEntityManagerPipelineFactory implements ChannelPipelineFactory {

    //public static Logger logger = Logger.getLogger("ssp");

	ExecutionHandler executionHandler;
	
	public HttpEntityManagerPipelineFactory(ExecutionHandler executionHandler) {
		this.executionHandler = executionHandler;
	}
	
	public ChannelPipeline getPipeline() throws Exception {
		//System.out.println("# getPipeline()");
		
		ChannelPipeline pipeline = Channels.pipeline();
		
		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
		pipeline.addLast("encoder", new HttpResponseEncoder());
		pipeline.addLast("deflater", new HttpContentCompressor());
		
		pipeline.addLast("answer formatter", new AnswerFormatter());
		pipeline.addLast("model formatter", new ModelFormatter());
		pipeline.addLast("statement cache", StatementCache.getInstance());
		pipeline.addLast("execution handler", executionHandler);
		pipeline.addLast("entity manager", EntityManager.getInstance());


        //logger.debug("[Factory] New pipeline created.");
		//System.out.println("# returning pipeline: " + pipeline);
		return pipeline;
	}
	
}
