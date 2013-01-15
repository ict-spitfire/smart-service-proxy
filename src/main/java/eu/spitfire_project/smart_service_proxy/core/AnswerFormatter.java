///**
// * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
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
//package eu.spitfire_project.smart_service_proxy.core;
//
//import java.io.ByteArrayOutputStream;
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.nio.charset.Charset;
//import org.jboss.netty.buffer.ChannelBuffers;
//import org.jboss.netty.channel.ChannelEvent;
//import org.jboss.netty.channel.ChannelFuture;
//import org.jboss.netty.channel.ChannelFutureListener;
//import org.jboss.netty.channel.ChannelHandlerContext;
//import org.jboss.netty.channel.DownstreamMessageEvent;
//import org.jboss.netty.channel.ExceptionEvent;
//import org.jboss.netty.channel.MessageEvent;
//import org.jboss.netty.channel.SimpleChannelHandler;
//import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
//import org.jboss.netty.handler.codec.http.HttpHeaders;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.jboss.netty.handler.codec.http.HttpResponse;
//import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
//import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
//import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
//
///**
// * @author Henning Hasemann
// */
//public class AnswerFormatter extends SimpleChannelHandler {
//	HttpRequest request = null;
//
//	@Override
//	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
//		Object m = e.getMessage();
//
//		if(m instanceof HttpRequest) {
//			request = (HttpRequest) m;
//		}
//		super.messageReceived(ctx, e);
//	}
//
//	@Override
//	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
//		Object message = e.getMessage();
//
//		if(message instanceof Answer) {
//			Answer answer = (Answer)message;
//			HttpResponse response = answer.toHttpResponse();
//			response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());
//			ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));
//		}
//		else {
//            ctx.sendDownstream(e);
//			//super.writeRequested(ctx, e);
//		}
//	}
//}
//
