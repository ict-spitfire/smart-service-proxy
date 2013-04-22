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
package eu.spitfire_project.smart_service_proxy.core.httpServer;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;
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

public class ModelCache extends SimpleChannelHandler {

    private static Logger log = Logger.getLogger(ModelCache.class.getName());
    
    private ConcurrentHashMap<URI, CacheElement> cache = new ConcurrentHashMap<URI, CacheElement>();

    private static ModelCache instance = new ModelCache();

    private ModelCache(){
    }

    public static ModelCache getInstance(){
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
        final URI targetUri = URI.create("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri());

        log.debug("Look up resoure " + targetUri);


        //Try to get a statement from the cache
        CacheElement ce = cache.get(targetUri);

        if (ce != null) {

            if (!ce.expiry.before(new Date())) {

                log.debug("Fresh model found for " + targetUri);

                //Send cached resource
                ChannelFuture future = Channels.write(ctx.getChannel(), ce.model);
                future.addListener(ChannelFutureListener.CLOSE);

                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        log.debug("Cached model for " + targetUri + " sent.");
                    }
                });

                return;
            }
            else {
                log.debug("Found expired statement for " + targetUri +
                            ". Trying to get a fresh one.");
            }
        }
        ctx.sendUpstream(me);
    }

    /**
     * This method is invoked for downstream {@link MessageEvent}s. If the {@link MessageEvent} contains an instance of
     * {@link eu.spitfire_project.smart_service_proxy.core.SelfDescription] as the message the contained {@link Model} instance will be cached. This instance of
     * {@link Model} will be sent further downstream afterwards.
     * 
     * @param ctx The {@link ChannelHandlerContext} to relate this handler with its current {@link Channel}
     * @param me The {@link MessageEvent} containing the {@link eu.spitfire_project.smart_service_proxy.core.SelfDescription }
     * @throws Exception
     */
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
            throws Exception {

        if (!(me.getMessage() instanceof SelfDescription)){
            log.debug("Received message of type: " + me.getMessage().getClass().getName());
            ctx.sendDownstream(me);
            return;
        }

        SelfDescription sd = (SelfDescription) me.getMessage();

        log.debug("Received SelfDescription of resource " + sd.getLocalURI() + " to be cached.");

        //Store new Element in Cache
        cache.put(new URI(sd.getLocalURI()), new CacheElement(sd.getModel(), sd.getExpiry()));
        log.info("Fresh status of " + sd.getLocalURI() + " saved in cache.");
        //Channels.write(ctx, me.getFuture(), sd.getModel());

        if(sd.isObservedResourceUpdate()){
            log.debug("Succesfully stored status update from observed resource " + sd.getLocalURI());
            me.getFuture().setSuccess();
            return;
        }

        log.debug("Cached status for " + sd.getLocalURI() + ". Send response to " + me.getRemoteAddress());
        DownstreamMessageEvent downstreamMessageEvent =
                new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), sd.getModel(), me.getRemoteAddress());

        ctx.sendDownstream(downstreamMessageEvent);

    }

//    public void updateCache(SelfDescription sd){
//        if(!sd.isObservedResourceUpdate()){
//            log.error("This method is only for updates from observed resources!");
//            return;
//        }
//
//        log.debug("Received update of resource: " + sd.getLocalURI());
//        try {
//            cache.put(new URI(sd.getLocalURI()), new CacheElement(sd.getModel(), sd.getExpiry()));
//        } catch (URISyntaxException e) {
//            log.error("Error while updating observed resource in cache.", e);
//        }
//    }
    //Wrapper class to add the expiry date to the cached model.
    //TODO use observe
    private class CacheElement {
        public Date expiry;
        public Model model;
        //public long observe;

        public CacheElement(Model m, Date e) {
            expiry = e;
            model = m;
            //observe = o;
        }
    }
}

