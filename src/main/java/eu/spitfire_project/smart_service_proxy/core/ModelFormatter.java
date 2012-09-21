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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * The @link{ModelFormatter} recognizes the requested mimetype from the incoming @link{HTTPRequest}. The payload of the corresponding
 * @link{Response} will be converted to the requested mimetype. If the requested mimetype is not available, the @link{ModelFormatter} sends
 * a standard @link{HttpResponse} with status code 415 (Unsupported media type).  
 * 
 * @author Oliver Kleine
 * @author Henning Hasemann * 
 */
public class ModelFormatter extends SimpleChannelHandler {
    
	private HttpRequest httpRequest;
	private Logger log = Logger.getLogger(ModelFormatter.class.getName());
    public static String DEFAULT_MODEL_LANGUAGE = "RDF/XML";
    public static String DEFAULT_RESPONSE_MIME_TYPE = "application/rdf+xml";
    
	/**
	 * Expected:
	 * - HTTP Request
	 * (remembers requested mime type for response)
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
		Object m = me.getMessage();

		if(m instanceof HttpRequest) {
            log.debug("[ModelFormatter] Received httpRequest for " + ((HttpRequest) m).getUri());

			httpRequest = (HttpRequest) m;

            String acceptHeader = httpRequest.getHeader("Accept");
            log.debug("Accept: " + acceptHeader);
		}
		ctx.sendUpstream(me);
	}
	
	/**
	 * Outbound Message types:
	 * - String
	 *	 (http response in remembered mime type)
	 */
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        
		if(me.getMessage() instanceof Model) {
			Model model = (Model) me.getMessage();
			ByteArrayOutputStream os = new ByteArrayOutputStream();

			String lang = DEFAULT_MODEL_LANGUAGE;
            String mimeType = DEFAULT_RESPONSE_MIME_TYPE;
            
			if(httpRequest != null) {
				String acceptHeader = httpRequest.getHeader("Accept");
				log.debug("Accept: " + acceptHeader);

				if(acceptHeader != null) {
					if(acceptHeader.indexOf("application/rdf+xml") != -1){
                        lang = "RDF/XML";
                        mimeType = "application/rdf+xml";
                    }
					else if(acceptHeader.indexOf("application/xml") != -1){
                        lang = "RDF/XML";
                        mimeType = "application/xml";
                    }
					else if(acceptHeader.indexOf("text/n3") != -1){
                        lang = "N3";
                        mimeType = "text/n3";
                    }
					else if(acceptHeader.indexOf("text/turtle") != -1) {
                        lang = "TURTLE";
                        mimeType = "text/turtle";
                    }
				}
			}
			
			model.write(os, lang);
			
			HttpResponse response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
			response.setHeader(CONTENT_TYPE, mimeType + "; charset=utf-8");
			response.setContent(ChannelBuffers.copiedBuffer(os.toString(), Charset.forName("UTF-8")));
			response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());

            //Channels.write(ctx, me.getFuture(), response);
            DownstreamMessageEvent dme =
                    new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), response, me.getRemoteAddress());
            ctx.sendDownstream(dme);
		}
		else {
			ctx.sendDownstream(me);
		}
	}
}

