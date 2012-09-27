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

import com.hp.hpl.jena.rdf.model.Model;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks whether the incoming {@link HttpRequest} can be answered with cached information. This depends on the
 * existence of cached information and its age. If there is suitable information available, the request will be
 * answered by sending a corresponding Object of type @link{Model} to the downstream. Otherwise the request will
 * be send to the upstream unchanged to be processed by the {@link EntityManager}.
 *
 * @author Oliver Kleine
 *
 */

public class StatementCache extends SimpleChannelHandler {

    private static Logger log = Logger.getLogger(StatementCache.class.getName());
    
    private ConcurrentHashMap<String, CacheElement> cache = new ConcurrentHashMap<String, CacheElement>();

    private static StatementCache instance = new StatementCache();

    private StatementCache(){
    }

    public static StatementCache getInstance(){
        return instance;
    }

    /**
     * This method is invoked for upstream {@link MessageEvent}s and handles incoming {@link HttpRequest}s.
     * It tries to find a fresh statement of the requested resource (identified using the requests target URI) in its
     * internal cache. If a fresh statement is found, it sends this statement (as an instance of {@link Model})
     * downstream to the {@link ModelFormatter}. 
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

        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        String targetUri = "http://" + httpRequest.getHeader("HOST") + httpRequest.getUri();

        log.debug("[StatementCache] Look up resoure " + targetUri);

        //Try to get a statement from the cache
        CacheElement ce = cache.get(targetUri);

        if (ce != null) {

            if (!ce.expiry.before(new Date())) {

                log.debug("[StatementCache] Fresh statement found for " + targetUri);

                //Send cached resource
                ChannelFuture future = Channels.write(ctx.getChannel(), ce.model);
                future.addListener(ChannelFutureListener.CLOSE);

                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        log.debug("[StatementCache] Cached statement for " + httpRequest.getUri() + " sent");
                    }
                });

                return;
            }
            else {
                log.debug("[StatementCache] Found expired statement for: " + httpRequest.getUri() +
                            ". Trying to get a fresh one.");
            }
        }
        ctx.sendUpstream(me);
    }

    /**
     * This method is invoked for downstream {@link MessageEvent}s. If the {@link MessageEvent} contains an instance of
     * {@link SelfDescription] as the message the contained {@link Model} instance will be cached. This instance of
     * {@link Model} will be sent further downstream afterwards.
     * 
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me The {@link MessageEvent} containing the {@link SelfDescription}
     * @throws Exception
     */
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
            throws Exception {

        if (me.getMessage() instanceof SelfDescription) {
            SelfDescription sd = (SelfDescription) me.getMessage();

            log.debug("[StatementCache] Received SelfDescription of resource " + sd.getLocalURI() + " to be cached.");
            
            //Store new Element in Cache
            //cache.put(sd.getLocalURI(), new CacheElement(sd.getModel(), sd.getExpiry(), sd.getObserve()));
            //Channels.write(ctx, me.getFuture(), sd.getModel());

            DownstreamMessageEvent downstreamMessageEvent =
                    new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), sd.getModel(), me.getRemoteAddress());

            ctx.sendDownstream(downstreamMessageEvent);
        }
        else{
            ctx.sendDownstream(me);
        }

    }

    //Wrapper class to add the expiry date to the cached model.
    //TODO use observe
    private class CacheElement {
        public Date expiry;
        public Model model;
        public long observe;

        public CacheElement(Model m, Date e, long o) {
            expiry = e;
            model = m;
            observe = o;
        }
    }
}

