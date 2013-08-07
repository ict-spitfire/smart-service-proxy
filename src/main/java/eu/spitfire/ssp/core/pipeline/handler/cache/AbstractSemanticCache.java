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
package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.google.common.net.InetAddresses;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.Main;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
* Checks whether the incoming {@link HttpRequest} can be answered with cached information. This depends on the
* existence of cached information and its age. If there is suitable information available, the request will be
* answered by sending a corresponding Object of type @link{Model} to the downstream. Otherwise the request will
* be send to the upstream unchanged to be processed by the {@link eu.spitfire.ssp.core.pipeline.handler.HttpRequestDispatcher}.
*
* @author Oliver Kleine
*
*/

public abstract class AbstractSemanticCache extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(AbstractSemanticCache.class.getName());

    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh statement of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh statement is found, it sends this statement (as an instance of {@link Model})
     * downstream to the {@link eu.spitfire.ssp.core.pipeline.handler.PayloadFormatter}.
     *
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me The {@link MessageEvent} containing the {@link HttpRequest}
     * @throws Exception
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)throws Exception {

        if (!(me.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) me.getMessage();

        final URI resourceUri;
        String[] uriParts = httpRequest.getUri().split("/");
        if(uriParts.length < 2 || !isUriScheme(uriParts[1]))
            resourceUri = new URI("http", null, Main.SSP_DNS_NAME, Main.SSP_HTTP_SERVER_PORT,
                    httpRequest.getUri(), null, null);
        else{
            //Scheme
            String scheme = uriParts[1];
            //Host and path
            String host = null;
            String path = "";
            if(uriParts.length > 2 && InetAddresses.isInetAddress(uriParts[2])){
                host = uriParts[2];
                for(int i = 3; i < uriParts.length; i++)
                    path += "/" + uriParts[i];
            }
            resourceUri =  new URI(scheme, null, host, -1, path, null, null);

        }

        log.debug("Lookup resource with URI: {}", resourceUri);
        Model cachedResource = findCachedResource(resourceUri);

        if(cachedResource != null){
            ChannelFuture future = Channels.write(ctx.getChannel(), cachedResource);
            future.addListener(ChannelFutureListener.CLOSE);

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    log.debug("Cached status for " + resourceUri + " sent.");
                }
            });
        }
        else{
            ctx.sendUpstream(me);
        }
    }

    public abstract Model findCachedResource(URI resourceUri);

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
            throws Exception {

        if(!(me.getMessage() instanceof HttpResponse)){
            ctx.sendDownstream(me);
            return;
        }

        HttpResponse httpResponse = (HttpResponse) me.getMessage();

        //TODO: Get Jena Model from response
//        putResourceToCache(URI resourceUri, )

    }

    public abstract void putResourceToCache(URI resourceUri, Model model);


    private boolean isUriScheme(String string){
        if("coap".equals(string))
            return true;

        if("file".equals(string))
            return true;

        return false;
    }
}

