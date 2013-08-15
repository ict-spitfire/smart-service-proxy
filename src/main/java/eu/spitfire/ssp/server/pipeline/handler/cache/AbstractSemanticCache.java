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
package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
* Checks whether the incoming {@link HttpRequest} can be answered with cached information. This depends on the
* existence of cached information and its age. If there is suitable information available, the request will be
* answered by sending a corresponding Object of type @link{Model} to the downstream. Otherwise the request will
* be send to the upstream unchanged to be processed by the {@link eu.spitfire.ssp.server.pipeline.handler.HttpRequestDispatcher}.
*
* @author Oliver Kleine
*
*/

public abstract class AbstractSemanticCache extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(AbstractSemanticCache.class.getName());

    private static Map<Integer, String> defaultPorts = new HashMap<>();
    static{
        defaultPorts.put(80, "http");
        defaultPorts.put(5683, "coap");
    }

    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh status of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh status is found, it sends this status (as an instance of {@link Model})
     * downstream to the {@link eu.spitfire.ssp.server.pipeline.handler.SemanticPayloadFormatter}.
     *
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me The {@link MessageEvent} potentially containing the {@link HttpRequest}
     *
     * @throws Exception in case of an error
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if (!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) me.getMessage();

        URI proxyUri = new URI(httpRequest.getUri());
        URI resourceUri;

        if(proxyUri.getQuery() != null && proxyUri.getQuery().startsWith("uri="))
            resourceUri = new URI(proxyUri.getQuery().substring(4));
        else
            resourceUri = new URI("http", null, Main.SSP_DNS_NAME,
                                  Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT,
                                  proxyUri.getPath(), proxyUri.getQuery(), proxyUri.getFragment());

        log.debug("Lookup resource with URI: {}", resourceUri);
        ResourceStatusMessage cachedResource = getCachedResource(resourceUri);

        if(cachedResource != null){
            log.debug("Cached status for {} found.", resourceUri);

            ChannelFuture future = Channels.future(ctx.getChannel());
            Channels.write(ctx, future, cachedResource, me.getRemoteAddress());
            future.addListener(ChannelFutureListener.CLOSE);
        }
        else{
            log.debug("NO cached status for {} found. Try to get a fresh one.", resourceUri);
            ctx.sendUpstream(me);
        }
    }

    /**
     * Returns an instance of {@link Model} that represents the resource identified by the given {@link URI}.
     *
     * @param resourceUri the {@link URI} identifying the wanted resource
     * @return the {@link Model} representing the status of the wanted resource
     */
    public abstract ResourceStatusMessage getCachedResource(URI resourceUri);

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
            throws Exception {

        if(me.getMessage() instanceof ResourceStatusMessage){
            log.debug("Downstream: {}", me.getMessage());
            ResourceStatusMessage updateMessage = (ResourceStatusMessage) me.getMessage();

            //Update Resource
            log.debug("Put fresh resource status for {} into cache.", updateMessage.getResourceUri());
            putResourceToCache(updateMessage.getResourceUri(), updateMessage.getResourceStatus(),
                    updateMessage.getExpiry());
        }

        ctx.sendDownstream(me);
    }

    /**
     * Insert a new resource into the cache or updated an already cached one. The expiry is given to enable the
     * cache to delete the resource status from the cache when its no longer valid.
     *
     * @param resourceUri the {@link URI} identifying the resource to be cached
     * @param model the {@link Model} representing the resource status to be cached
     * @param expiry the expiry of the resource status to be cached
     */
    public abstract void putResourceToCache(URI resourceUri, Model model, Date expiry);

    /**
     * For future use! Method to delete a cached resource from the cache (not yet used by the framework).
     * @param resourceUri the {@link URI} identifying the cached resource who's status is to be deleted
     */
    public abstract void deleteResource(URI resourceUri);

}

