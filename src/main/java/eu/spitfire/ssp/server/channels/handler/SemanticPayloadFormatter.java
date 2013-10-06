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
package eu.spitfire.ssp.server.channels.handler;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * The {@link SemanticPayloadFormatter} recognizes the requested mimetype from the incoming {@link HttpRequest}.
 * The payload of the corresponding {@link HttpResponse} will be converted to the requested mimetype, i.e. the
 * supported one with the highest priority.
 *
 * If none of the the requested mimetype(s) is available, the {@link SemanticPayloadFormatter} sends
 * a {@link HttpResponse} with status code 415 (Unsupported media type).
 *
 * @author Oliver Kleine
 */
public class SemanticPayloadFormatter extends SimpleChannelHandler {

    public static final Language DEFAULT_LANGUAGE = Language.RDF_N3;
    public static final long MILLIS_PER_YEAR = 31536000730L;

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

            if(acceptedLanguage == null){
                acceptedLanguage = DEFAULT_LANGUAGE;
                log.info("Could not find any language, which is both, accepted and supported. Use default ({})", acceptedLanguage);
            }
            else{
                log.info("Language with highest priority which is both, accepted and supported: {}", acceptedLanguage);
            }


            httpVersion = httpRequest.getProtocolVersion();
		}

		ctx.sendUpstream(me);
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, final MessageEvent me) throws Exception {
        log.debug("Downstream: {}", me.getMessage());

        if(me.getMessage() instanceof InternalResourceStatusMessage){
            final InternalResourceStatusMessage internalResourceStatusMessage = (InternalResourceStatusMessage) me.getMessage();

            Model model = internalResourceStatusMessage.getModel();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            //Serialize the model associated with the resource and write on OutputStream
            model.write(byteArrayOutputStream, acceptedLanguage.lang);

            HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, OK);

            ChannelBuffer payload = ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray());
            httpResponse.setContent(payload);

            httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, acceptedLanguage.mimeType);
            httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());
            if(internalResourceStatusMessage.getExpiry() == null)
                httpResponse.setHeader(HttpHeaders.Names.EXPIRES,
                        dateFormat.format(new Date(System.currentTimeMillis() + MILLIS_PER_YEAR)));
            else
                httpResponse.setHeader(HttpHeaders.Names.EXPIRES,
                        dateFormat.format(internalResourceStatusMessage.getExpiry()));

            httpResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");

            //Write HTTP response to remote host, i.e. the client
            Channels.write(ctx, me.getFuture(), httpResponse, me.getRemoteAddress());

            me.getFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    log.info("Status of {} (encoded in {}) successfully written to {}.",
                            new Object[]{internalResourceStatusMessage.getResourceUri(), acceptedLanguage.lang,
                            me.getRemoteAddress()});
                }
            });

            return;
        }

        if(me.getMessage() instanceof HttpResponse){
            ((HttpResponse) me.getMessage()).setHeader("Access-Control-Allow-Origin", "*");
            ((HttpResponse) me.getMessage()).setHeader("Access-Control-Allow-Credentials", "true");
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
}

