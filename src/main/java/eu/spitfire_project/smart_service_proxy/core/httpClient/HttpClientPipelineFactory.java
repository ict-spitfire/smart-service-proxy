package eu.spitfire_project.smart_service_proxy.core.httpClient;

/*
2    * Copyright 2009 Red Hat, Inc.
3    *
4    * Red Hat licenses this file to you under the Apache License, version 2.0
5    * (the "License"); you may not use this file except in compliance with the
6    * License.  You may obtain a copy of the License at:
7    *
8    *    http://www.apache.org/licenses/LICENSE-2.0
9    *
10   * Unless required by applicable law or agreed to in writing, software
11   * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
12   * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
13   * License for the specific language governing permissions and limitations
14   * under the License.
15   */

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;

import static org.jboss.netty.channel.Channels.pipeline;

/**
  * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
  * @author Andy Taylor (andy.taylor@jboss.org)
32   * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 33   *
 34   * @version $Rev: 2226 $, $Date: 2010-03-31 11:26:51 +0900 (Wed, 31 Mar 2010) $
 35   */
public class HttpClientPipelineFactory implements ChannelPipelineFactory {

    HttpClient responseHandler;

    public HttpClientPipelineFactory(HttpClient client) {
        this.responseHandler = client;
    }

    public ChannelPipeline getPipeline() throws Exception {
        //Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        pipeline.addLast("codec", new HttpClientCodec());
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("handler", responseHandler);

        return pipeline;
    }
}
