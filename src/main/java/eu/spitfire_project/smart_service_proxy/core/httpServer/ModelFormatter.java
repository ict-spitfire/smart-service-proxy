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

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.ResourceUtils;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.*;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * The {@link ModelFormatter} recognizes the requested mimetype from the incoming {@link HttpRequest}. The payload of the corresponding
 * {@link HttpResponse} will be converted to the requested mimetype. If the requested mimetype is not available, the {@link ModelFormatter} sends
 * a standard {@link HttpResponse} with status code 415 (Unsupported media type).
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
            httpRequest = (HttpRequest) m;

            log.debug("Incoming HttpRequest for "
                    + URI.create("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri())
                    + " accepts " + httpRequest.getHeader("Accept"));
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
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

			String lang = DEFAULT_MODEL_LANGUAGE;
            String mimeType = DEFAULT_RESPONSE_MIME_TYPE;
            
			if(httpRequest != null) {
				String acceptHeader = httpRequest.getHeader("Accept");
				log.debug("Accept Header of Request: " + acceptHeader);

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

            //Rename CoAP URIs of subjects contained in the payload to HTTP URIs
            ResIterator iterator = model.listSubjects();
            while(iterator.hasNext()){
                Resource subject = iterator.nextResource();
                String uri = subject.getURI();
                if(uri != null && uri.startsWith("coap://")){
                    ResourceUtils.renameResource(subject, createHttpMirrorUri(uri));
                }
            }

            //Rename CoAP URIs of objects contained in the payload to HTTP URIs
            NodeIterator iterator2 = model.listObjects();
            while(iterator2.hasNext()){
                RDFNode object = iterator2.nextNode();
                if(object != null  && object.isResource()){
                    Resource objectResource = object.asResource();
                    String uri = objectResource.getURI();
                    if(uri != null && uri.startsWith("coap://")){
                        ResourceUtils.renameResource(objectResource, createHttpMirrorUri(uri));
                    }
                }
            }

            try{
                //Serialize model and write on OutputStream
                model.write(byteArrayOutputStream, lang);

                //Create Payload and
                HttpResponse response =
                        new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                response.setHeader(CONTENT_TYPE, mimeType + "; charset=utf-8");
                response.setContent(ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray()));
                response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());

                DownstreamMessageEvent dme =
                        new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), response, me.getRemoteAddress());
                ctx.sendDownstream(dme);
            }
            catch(Exception e){
                log.debug("Error while converting payload" + mimeType, e);
                HttpResponse response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                String message = "Error while converting payload to " + mimeType + ".";
                response.setContent(ChannelBuffers.copiedBuffer(message.getBytes(Charset.forName("UTF-8"))));
                response.setHeader(CONTENT_TYPE, "text/plain; charset=utf-8");

                DownstreamMessageEvent dme =
                        new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), response, me.getRemoteAddress());
                ctx.sendDownstream(dme);
            }
		}
		else {
			ctx.sendDownstream(me);
		}
	}

    public String shortenIpv6Address(String ipv6Address){

        //remove leading zeros per block
        ipv6Address = ipv6Address.replaceAll(":0000", ":0");
        ipv6Address = ipv6Address.replaceAll(":000", ":0");
        ipv6Address = ipv6Address.replaceAll(":00", ":0");
        ipv6Address = ipv6Address.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");

        //return shortened IP
        ipv6Address = ipv6Address.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");

        return ipv6Address;

    }

    public String createHttpMirrorUri(String coapUri){
        String ipv6Address = coapUri.substring(coapUri.indexOf("[") + 1, coapUri.indexOf("]"));
        log.debug("IPv6 Address vorher: " + ipv6Address);

        ipv6Address = shortenIpv6Address(ipv6Address);


        log.debug("IPv6 Address: " + ipv6Address);

        String path = coapUri.substring(coapUri.indexOf("/", 8));

        return "http://" + ipv6Address.replace(":", "-")
                         + "." + EntityManager.DNS_WILDCARD_POSTFIX
                         + ":" + EntityManager.SSP_HTTP_SERVER_PORT
                         + path;
    }

    public static void main(String[] args) throws FileNotFoundException, UnknownHostException {
        ModelFormatter instance = new ModelFormatter();

        File file = new File("/home/olli/impl/smart-service-proxy/rdf.txt");
        FileInputStream istream = new FileInputStream(file);

        Model model = ModelFactory.createDefaultModel();
        model.read(istream, null);

        String iptest = "fd00:db08:0:c0a1:215:8d00:11:a88";
        System.out.println(iptest);
        String result = instance.shortenIpv6Address(iptest);
        System.out.println(result);

        iptest = "2001:db8:0000:0001:0011:0111:1111:1";
        System.out.println(iptest);
        result = instance.shortenIpv6Address(iptest);
        System.out.println(result);

        ResIterator iterator = model.listSubjects();

        while(iterator.hasNext()){
            Resource subject = iterator.nextResource();
            String uri = subject.getURI();
            if(uri.startsWith("coap://")){
                ResourceUtils.renameResource(subject, instance.createHttpMirrorUri(uri));
            }
        }

        NodeIterator iterator2 = model.listObjects();

        while(iterator2.hasNext()){
            RDFNode object = iterator2.nextNode();
            if(object.isResource()){
                Resource objectResource = object.asResource();
                String uri = objectResource.getURI();
                if(uri.startsWith("coap://")){
                    ResourceUtils.renameResource(objectResource, instance.createHttpMirrorUri(uri));
                }
            }
        }

        StringWriter writer = new StringWriter();

        try{
            //model.write(writer, "RDF/XML");
            //System.out.println("[SelfDescription] Output after Model serialization (RDF/XML):\n " + writer.toString());
        }
        catch(Exception e){
            System.out.println("Could not write RDF/XML");
            e.printStackTrace();
        }
    }
}

