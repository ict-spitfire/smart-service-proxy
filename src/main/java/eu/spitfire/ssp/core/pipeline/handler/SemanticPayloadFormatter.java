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
package eu.spitfire.ssp.core.pipeline.handler;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.core.payloadserialization.Language;
import eu.spitfire.ssp.core.payloadserialization.ModelSerializer;
import eu.spitfire.ssp.core.payloadserialization.ShdtDeserializer;
import eu.spitfire.ssp.core.pipeline.handler.cache.ResourceStatusMessage;
import eu.spitfire.ssp.gateway.coap.requestprocessing.CoapCodeHttpStatusMapper;
import eu.spitfire.ssp.gateway.coap.requestprocessing.CoapOptionHttpHeaderMapper;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * The {@link SemanticPayloadFormatter} recognizes the requested mimetype from the incoming {@link HttpRequest}. The payload
 * of the corresponding {@link HttpResponse} will be converted to the requested mimetype, i.e. the supported one with the
 * highest priority. If none of the the requested mimetype(s) is available, the {@link SemanticPayloadFormatter} sends
 * a {@link HttpResponse} with status code 415 (Unsupported media type).
 *
 * @author Oliver Kleine
 */
public class SemanticPayloadFormatter extends SimpleChannelHandler {

    public static Language DEFAULT_LANGUAGE = Language.RDF_XML;

    private static Logger log = LoggerFactory.getLogger(SemanticPayloadFormatter.class.getName());
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    static{
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private Language acceptedLanguage;
    private HttpVersion httpVersion;


	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

		if(me.getMessage() instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) me.getMessage();
            String acceptHeader = httpRequest.getHeader(HttpHeaders.Names.ACCEPT);

            if(acceptHeader != null){
                Multimap<Double, String> acceptedMediaTypes = getAcceptedMediaTypes(acceptHeader);

                acceptLookup:
                for(Double priority : acceptedMediaTypes.keySet()){
                    for(String mimeType : acceptedMediaTypes.get(priority)){
                        acceptedLanguage = Language.getByHttpMimeType(mimeType);
                        if(acceptedLanguage != null){
                           break acceptLookup;
                        }
                    }
                }
            }

            if(acceptedLanguage == null)
                acceptedLanguage = DEFAULT_LANGUAGE;

            log.debug("Accepted language: {}", acceptedLanguage);
            httpVersion = httpRequest.getProtocolVersion();
		}

		ctx.sendUpstream(me);
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.debug("Downstream: {}", me.getMessage());

        if(me.getMessage() instanceof ResourceStatusMessage){
            ResourceStatusMessage internalResourceUpdateMessage =
                    (ResourceStatusMessage) me.getMessage();

            Model resourceStatus = internalResourceUpdateMessage.getResourceStatus();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            //Serialize model and write on OutputStream
            resourceStatus.write(byteArrayOutputStream, acceptedLanguage.lang);

            HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, OK);

            ChannelBuffer payload = ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray());
            httpResponse.setContent(payload);

            httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, acceptedLanguage.mimeType);
            httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());
            httpResponse.setHeader(HttpHeaders.Names.EXPIRES,
                    dateFormat.format(internalResourceUpdateMessage.getExpiry()));

            httpResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            Channels.write(ctx, me.getFuture(), httpResponse, me.getRemoteAddress());
            return;
        }

        ctx.sendDownstream(me);
    }


    /**
     * Returns a {@link com.google.common.collect.Multimap}  with priorities as key and the name of the
     * accepted HTTP media type as value. The {@link com.google.common.collect.Multimap#keySet()} is guaranteed to
     * contain the keys ordered according to their priorities with the highest priority first.
     *
     * @param headerValue the value of an HTTP Accept-Header
     *
     * @return a {@link com.google.common.collect.Multimap}  with priorities as key and the name of the
     * accepted HTTP media type as value.
     */
    public static Multimap<Double, String> getAcceptedMediaTypes(String headerValue){

        Multimap<Double, String> result = TreeMultimap.create();

        for(String acceptedMediaType : headerValue.split(",")){
            ArrayList<String> parts = new ArrayList<>(Arrays.asList(acceptedMediaType.split(";")));

            if(log.isDebugEnabled()){
                log.debug("Media type (from HTTP ACCEPT header): {}.", acceptedMediaType);
                for(int i = 0; i < parts.size(); i++){
                    log.debug("Part {}: {}", i + 1, parts.get(i));
                }
            }

            double priority = 1.0;

            if(parts.size() > 1){
                String priorityString = parts.get(parts.size() - 1).replace(" ", "");
                if(priorityString.startsWith("q=")){
                    priority = Double.parseDouble(priorityString.substring(priorityString.indexOf("=") + 1));
                    parts.remove(parts.size() - 1);
                }
            }


            String httpMediaType;
            if(parts.size() > 1)
                httpMediaType = (parts.get(0) + ";" + parts.get(1)).replace(" ", "");
            else
                httpMediaType = parts.get(0).replace(" ", "");

            log.debug("Found accepted media type {} with priority {}.", httpMediaType, priority);

            if(httpMediaType.contains("*")){
                log.warn("There is no support for wildcard types ({})", httpMediaType);
                continue;
            }

            result.put(priority * (-1), httpMediaType);
            log.debug("Added media type {} with priority {}.", httpMediaType, priority);

        }

        return result;
    }




    public HttpResponse convertToHttpResponse(CoapResponse coapResponse, HttpVersion httpVersion,
                                                     Language acceptedMimeType){

        //convert status code / response code
        HttpResponseStatus httpStatus = CoapCodeHttpStatusMapper.getHttpResponseStatus(coapResponse.getCode());
        if(httpStatus == null)
            httpStatus = INTERNAL_SERVER_ERROR;

        //check for potential errors
        if(coapResponse.getCode().isErrorMessage())
            return HttpResponseFactory.createHttpErrorResponse(httpVersion, httpStatus,
                    "CoAP response had error code " + coapResponse.getCode());

        if(coapResponse.getContentType() == null)
            return HttpResponseFactory.createHttpErrorResponse(httpVersion, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "CoAP response without content type option.");


        //read payload from CoAP response
        byte[] coapPayload = new byte[coapResponse.getPayload().readableBytes()];
        coapResponse.getPayload().getBytes(0, coapPayload);

        ChannelBuffer httpResponsePayload;

        //Create payload for HTTP response
        if(coapResponse.getContentType() == OptionRegistry.MediaType.APP_SHDT){
            log.debug("SHDT payload in CoAP response.");

            Model model = ModelFactory.createDefaultModel();

            try{
                (new ShdtDeserializer(64)).read_buffer(model, coapPayload);
            }
            catch(Exception e){
                log.error("SHDT error!", e);
                return HttpResponseFactory.createHttpErrorResponse(httpVersion,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
            }

            //Serialize payload for HTTP response
            httpResponsePayload = ModelSerializer.serializeModel(model, acceptedMimeType);
        }
        else{
            httpResponsePayload = coapResponse.getPayload();
        }

        //Create HTTP header fields
        Multimap<String, String> httpHeaders = CoapOptionHttpHeaderMapper.getHttpHeaders(coapResponse.getOptionList());

        //Create HTTP response
        HttpResponse httpResponse =
                HttpResponseFactory.createHttpResponse(httpVersion, httpStatus, httpHeaders, httpResponsePayload);

        //Set HTTP content type header
        httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, acceptedMimeType.mimeType);

        return httpResponse;
    }

//    if(httpRequest.getMethod() == HttpMethod.GET){
//        String acceptHeader = httpRequest.getHeader("Accept");
//        log.debug("Accept header of request: {}.", acceptHeader);
//
//        String lang = DEFAULT_MODEL_LANGUAGE;
//        String mimeType = DEFAULT_RESPONSE_MIME_TYPE;
//
//        if(acceptHeader != null) {
//            if(acceptHeader.indexOf("application/rdf+xml") != -1){
//                lang = "RDF/XML";
//                mimeType = "application/rdf+xml";
//            }
//            else if(acceptHeader.indexOf("application/xml") != -1){
//                lang = "RDF/XML";
//                mimeType = "application/xml";
//            }
//            else if(acceptHeader.indexOf("text/n3") != -1){
//                lang = "N3";
//                mimeType = "text/n3";
//            }
//            else if(acceptHeader.indexOf("text/turtle") != -1) {
//                lang = "TURTLE";
//                mimeType = "text/turtle";
//            }
//        }
//
//        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//
//        model.write(byteArrayOutputStream, lang);
//
//        //Create Payload and
//        httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
//        httpResponse.setHeader(CONTENT_TYPE, mimeType + "; charset=utf-8");
//        httpResponse.setContent(ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray()));
//        httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());
//    }
//    else{
//        httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
//                HttpResponseStatus.METHOD_NOT_ALLOWED);
//
//        httpResponse.setHeader(CONTENT_TYPE, DEFAULT_RESPONSE_MIME_TYPE + "; charset=utf-8");
//        String message = "Method not allowed: " + httpRequest.getMethod();
//        httpResponse.setContent(ChannelBuffers.wrappedBuffer(message.getBytes(Charset.forName("UTF-8"))));
//        httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());
//    }
}

