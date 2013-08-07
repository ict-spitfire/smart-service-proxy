///**
// * Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
// * All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
// * following conditions are met:
// *
// *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
// *    disclaimer.
// *
// *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
// *    following disclaimer in the documentation and/or other materials provided with the distribution.
// *
// *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
// *    products derived from this software without specific prior written permission.
// *
// * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//package eu.spitfire.ssp.core.pipeline.handler;
//
//import org.jboss.netty.channel.ChannelHandlerContext;
//import org.jboss.netty.channel.MessageEvent;
//import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///**
// * This handler translates HTTPRequests which
// * are targeted to wildcard DNS entries.
// *
// * @author Stefan Hueske
// * @author Oliver Kleine
// */
//public class HttpMirrorUriHandler extends SimpleChannelUpstreamHandler {
//
//    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());
//
//    public static enum ProtocolName {
//        COAP("coap"),
//        FILE("file");
//
//        private String name;
//
//        private ProtocolName(String name){
//            this.name = name;
//        }
//
//        public String toString(){
//            return name;
//        }
//    }
//
//    /**
//     * Changes the requests host part to the IPv6 address of the target resource. This is to enable IPv4 clients to
//     * access IPv6 resources. E.g. a request for the resource http://2001-638--1.example.org/path would be transparently
//     * redirected to core://[2001:638::1]/path
//     *
//     * @param ctx The ChannelHandlerContext
//     * @param me The MessageEvent
//     * @throws Exception
//     */
//    @Override
//    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)  throws Exception{
//
//        if (!(me.getMessage() instanceof HttpRequest)){
//            ctx.sendUpstream(me);
//            return;
//        }
//
//        HttpRequest request = (HttpRequest) me.getMessage();
//        String path = request.getUri();
//
//        log.debug("Incoming HTTP request for path: {}.", path);
//
//        for(ProtocolName protocolName : ProtocolName.values()){
//            if(path.startsWith(protocolName.toString()))
//
//
//
//        //Eventually convert URIs host part to IPv6 address of the target host
//        if (host.endsWith(DNS_WILDCARD_POSTFIX) ) {
//            host = host.substring(0, host.indexOf(DNS_WILDCARD_POSTFIX) - 1);
//            host = host.replace("-", ":");
//            log.debug("New target host: " + host);
//
//            try{
//                request.setHeader(HOST, "[" + InetAddress.getByName("[" + host + "]").getHostAddress() + "]");
//            }
//            catch (UnknownHostException e) {
//                log.debug("This should never happen! Not an IPv6 address: " + host);
//            }
//        }
//    }
//
//        ctx.sendUpstream(me);
//        super.messageReceived(ctx, me);
//
//    }
//}
