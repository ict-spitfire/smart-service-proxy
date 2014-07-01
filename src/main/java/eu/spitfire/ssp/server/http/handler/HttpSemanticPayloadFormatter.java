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
*  - Neither the backendName of the University of Luebeck nor the names of its contributors may be used to endorse or promote
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
package eu.spitfire.ssp.server.http.handler;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import eu.spitfire.ssp.server.common.messages.*;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.utils.Language;
import eu.spitfire.ssp.utils.SparqlResultFormat;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The {@link HttpSemanticPayloadFormatter} recognizes the requested mimetype from the incoming {@link HttpRequest}.
 * The payload of the corresponding {@link HttpResponse} will be converted to the requested mimetype, i.e. the
 * supported one with the highest priority.
 *
 * For request addressed to URIs like "/?graph=..." and "/?resource=..." the supported content formats, i.e.
 * supported accept headers of HTTP requests are those defined in {@link eu.spitfire.ssp.utils.Language}. For
 * POST requests addressed to the SPARQL endpoint, i.e. URI "/sparql" the supported content formats are defined
 * in {@link eu.spitfire.ssp.utils.SparqlResultFormat}.
 *
 * @author Oliver Kleine
 */
public class HttpSemanticPayloadFormatter extends SimpleChannelHandler {

    public static final Language DEFAULT_LANGUAGE = Language.RDF_XML;
    public static final SparqlResultFormat DEFAULT_SPARQL_RESULT_FORMAT = SparqlResultFormat.XML;

    private static final int RDF_FORMAT = 1;
    private static final int SPARQL_RESULT_FORMAT = 2;

    private static Logger log = LoggerFactory.getLogger(HttpSemanticPayloadFormatter.class.getName());

    private int formatType;
    private Object acceptedFormat;
    private HttpVersion httpVersion;

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if(me.getMessage() instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) me.getMessage();
            String acceptHeader = httpRequest.headers().get(HttpHeaders.Names.ACCEPT);
            URI requestURI = new URI(httpRequest.getUri());

            //Request returns a graph to be serialized as RDF
            if(("/".equals(requestURI.getPath()) && requestURI.getQuery() != null)){

                if(requestURI.getQuery().contains("graph=") || requestURI.getQuery().contains("resource=")){
                    formatType = RDF_FORMAT;
                    if(acceptHeader != null){
                        Multimap<Double, String> acceptedMediaTypes = getAcceptedMediaTypes(acceptHeader);

                        acceptLookup:
                        for(Double priority : acceptedMediaTypes.keySet()){
                            for(String mimeType : acceptedMediaTypes.get(priority)){
                                acceptedFormat = Language.getByHttpMimeType(mimeType);
                                if(acceptedFormat != null){
                                    break acceptLookup;
                                }
                            }
                        }
                    }

                    if(acceptedFormat == null){
                        acceptedFormat = DEFAULT_LANGUAGE;
                        log.info("Could not find any language, which is both, accepted and supported. Use default ({})",
                                DEFAULT_LANGUAGE);
                    }
                    else{
                        log.info("Language with highest priority which is both, accepted and supported: {}", acceptedFormat);
                    }
                }
            }

            else if("/services/sparql-endpoint".equals(requestURI.getPath()) && httpRequest.getMethod() == HttpMethod.POST){
                formatType = SPARQL_RESULT_FORMAT;
                if(acceptHeader != null){
                    Multimap<Double, String> acceptedMediaTypes = getAcceptedMediaTypes(acceptHeader);

                    acceptLookup:
                    for(Double priority : acceptedMediaTypes.keySet()){
                        for(String mimeType : acceptedMediaTypes.get(priority)){
                            acceptedFormat = SparqlResultFormat.getByHttpMimeType(mimeType);
                            if(acceptedFormat != null){
                                break acceptLookup;
                            }
                        }
                    }
                }

                if(acceptedFormat == null){
                    acceptedFormat = DEFAULT_SPARQL_RESULT_FORMAT;
                    log.info("Could not find any SPARQL result format, which is both, accepted and supported. Use " +
                            "default ({})", DEFAULT_SPARQL_RESULT_FORMAT);
                }
                else{
                    log.info("SPARQL result format with highest priority which is both, accepted and supported: {}",
                            acceptedFormat);
                }
            }

            httpVersion = httpRequest.getProtocolVersion();
		}

		ctx.sendUpstream(me);
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, final MessageEvent me) throws Exception {
        log.debug("Downstream: {}", me.getMessage());

        HttpResponse httpResponse = null;

        if(me.getMessage() instanceof GraphStatusMessage){

            if(formatType == RDF_FORMAT){
                if(me.getMessage() instanceof EmptyGraphStatusMessage){
                    EmptyGraphStatusMessage graphStatusMessage = (EmptyGraphStatusMessage) me.getMessage();
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, graphStatusMessage);
                }

                else if(me.getMessage() instanceof ExpiringGraphStatusMessage){
                    ExpiringGraphStatusMessage graphStatusMessage = (ExpiringGraphStatusMessage) me.getMessage();
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, (Language) acceptedFormat,
                            graphStatusMessage);
                }

                else if(me.getMessage() instanceof GraphStatusErrorMessage){
                    GraphStatusErrorMessage graphStatusMessage = (GraphStatusErrorMessage) me.getMessage();
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, graphStatusMessage);
                }
            }

            else{
                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, "Format Type for GraphStatusMessage was " +
                        formatType);
            }
        }

        else if(me.getMessage() instanceof SparqlQueryResultMessage){
            if(formatType == SPARQL_RESULT_FORMAT){
                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                        (SparqlQueryResultMessage) me.getMessage(), (SparqlResultFormat) acceptedFormat);
            }

            else{
                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, "Format Type for SparqlQueryResultMessage was " +
                        formatType);
            }
        }

        else if(me.getMessage() instanceof HttpResponse){
            httpResponse = (HttpResponse) me.getMessage();
        }


        if(httpResponse == null){
            httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, "Unsupported internal message " + me.getMessage());
        }


        httpResponse.headers().add("Access-Control-Allow-Origin", "*");
        httpResponse.headers().add("Access-Control-Allow-Credentials", "true");

        Channels.write(ctx, me.getFuture(), httpResponse, me.getRemoteAddress());

        if(log.isInfoEnabled()){
            me.getFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    log.info("HTTP response written to {}.", me.getRemoteAddress());
                }
            });
        }
    }


    /**
     * Returns a {@link com.google.common.collect.Multimap}  with priorities as key and the backendName of the
     * accepted HTTP media type as value. The {@link com.google.common.collect.Multimap#keySet()} is guaranteed to
     * contain the keys ordered according to their priorities with the highest priority first.
     *
     * @param headerValue the value of an HTTP Accept-Header
     *
     * @return a {@link com.google.common.collect.Multimap}  with priorities as key and the backendName of the
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

