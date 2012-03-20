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
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks whether the incoming {@link HTTPRequest} can be answered with cached information. This depends on the
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
     * Expected:
     * - HTTP Request
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        Object msg = e.getMessage();
//		System.out.println("# Statementcache received: " + msg);

        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            CacheElement ce = cache.get(req.getUri());
            //System.out.println("[StatementChache] Look up resource: " + req.getUri());

            if (ce != null) {
          //      System.out.println("# [StatementChache] Resource found in cache: " + req.getUri());

                if (!ce.expiry.before(new Date())) {
                    ChannelFuture future = Channels.write(ctx.getChannel(), ce.model);
                    if (HttpHeaders.isKeepAlive(req)) {
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                    //System.out.println("[StatementChache] Resource is fresh.");
                    return;
                } else {
                    //System.out.println("[StatementChache] Resource is expired.");
                }
            }
        }
//		System.out.println("# Statementcache passing on: " + msg);
        super.messageReceived(ctx, e);
    }

    /**
     * Outbound Message types:
     * - Model
     */
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
            throws Exception {

        if (me.getMessage() instanceof SelfDescription) {
            SelfDescription sd = (SelfDescription) me.getMessage();

            log.debug("[StatementCache] Received SelfDescription of resource " + sd.getLocalURI() + " to be cached.");
            
            //Store new Element in Cache
            cache.put(sd.getLocalURI(), new CacheElement(sd.getModel(), sd.getExpiry(), sd.getObserve()));

            Channels.write(ctx, me.getFuture(), sd.getModel());
        }
        else{
            ctx.sendDownstream(me);
        }

    }

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

