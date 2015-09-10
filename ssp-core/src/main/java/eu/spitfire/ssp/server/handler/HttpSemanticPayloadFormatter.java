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
package eu.spitfire.ssp.server.handler;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import eu.spitfire.ssp.server.internal.utils.HttpResponseFactory;
import eu.spitfire.ssp.server.internal.utils.Language;
import eu.spitfire.ssp.server.internal.utils.QueryResultsFormat;
import org.apache.jena.query.ResultSetFormatter;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@link HttpSemanticPayloadFormatter} recognizes the requested mimetype from the incoming {@link HttpRequest}.
 * The payload of the corresponding {@link HttpResponse} will be converted to the requested mimetype, i.e. the
 * supported one with the highest priority.
 *
 * For request addressed to URIs like "/?graph=..." and "/?resource=..." the supported content formats, i.e.
 * supported accept headers of HTTP requests are those defined in {@link eu.spitfire.ssp.server.internal.utils.Language}. For
 * POST requests addressed to the SPARQL endpoint, i.e. URI "/sparql" the supported content formats are defined
 * in {@link eu.spitfire.ssp.server.internal.utils.QueryResultsFormat}.
 *
 * @author Oliver Kleine
 */
public class HttpSemanticPayloadFormatter extends SimpleChannelHandler {

    public static final Language DEFAULT_LANGUAGE = Language.RDF_TURTLE;
    public static final QueryResultsFormat DEFAULT_SPARQL_RESULT_FORMAT = QueryResultsFormat.XML;

//    private static final int RDF = 1;
//    private static final int SPARQL_RESULT = 2;

    private static Logger LOG = LoggerFactory.getLogger(HttpSemanticPayloadFormatter.class.getName());

//    private int responseFormatType;
//    private Object acceptedFormat;
//    private HttpVersion httpVersion;

//    private Object format;

    private HttpRequest httpRequest;

//    private static Object findJenaLanguage(Multimap<Double, String> acceptedMediaTypes){
//        Object format;
//        for(Double priority : acceptedMediaTypes.keySet()){
//            for(String mimeType : acceptedMediaTypes.get(priority)){
//                format = Language.getByHttpMimeType(mimeType);
//                if(format == null){
//                    format = QueryResultsFormat.getByHttpMimeType(mimeType);
//                }
//                if(format != null){
//                    return format;
//                }
//            }
//        }
//        return null;
//    }


	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if (me.getMessage() instanceof HttpRequest) {
            this.httpRequest = (HttpRequest) me.getMessage();
        }

        ctx.sendUpstream(me);
    }
//            // define format of response content
//            String acceptHeader = httpRequest.headers().get(HttpHeaders.Names.ACCEPT);
//            if(acceptHeader != null) {
//                this.format = identifyResponseContentFormat(getAcceptedMediaTypes(acceptHeader));
//            }
//
//
//            URI requestURI = new URI(httpRequest.getUri());
//
//
//
//
//            //Request returns a graph to be serialized as RDF
//            if(("/".equals(requestURI.getPath()) && requestURI.getQuery() != null)){
//
//                if(requestURI.getQuery().contains("graph=") || requestURI.getQuery().contains("resource=")){
//                    responseFormatType = RDF;
//                    if(acceptHeader != null){
//                        Multimap<Double, String> acceptedMediaTypes = getAcceptedMediaTypes(acceptHeader);
//
//                        acceptLookup:
//                        for(Double priority : acceptedMediaTypes.keySet()){
//                            for(String mimeType : acceptedMediaTypes.get(priority)){
//                                acceptedFormat = Language.getByHttpMimeType(mimeType);
//                                if(acceptedFormat != null){
//                                    break acceptLookup;
//                                }
//                            }
//                        }
//                    }
//
//                    if(acceptedFormat == null){
//                        acceptedFormat = DEFAULT_LANGUAGE;
//                        LOG.info("Could not find any language, which is both, accepted and supported. Use default ({})",
//                                DEFAULT_LANGUAGE);
//                    }
//                    else{
//                        LOG.info("Language with highest priority which is both, accepted and supported: {}", acceptedFormat);
//                    }
//                }
//            }
//
//            else if("/services/sparql-endpoint".equals(requestURI.getPath()) && httpRequest.getMethod() == HttpMethod.POST){
//                responseFormatType = SPARQL_RESULT;
//                if(acceptHeader != null){
//                    Multimap<Double, String> acceptedMediaTypes = getAcceptedMediaTypes(acceptHeader);
//
//                    acceptLookup:
//                    for(Double priority : acceptedMediaTypes.keySet()){
//                        for(String mimeType : acceptedMediaTypes.get(priority)){
//                            acceptedFormat = QueryResultsFormat.getByHttpMimeType(mimeType);
//                            if(acceptedFormat != null){
//                                break acceptLookup;
//                            }
//                        }
//                    }
//                }
//
//                if(acceptedFormat == null){
//                    acceptedFormat = DEFAULT_SPARQL_RESULT_FORMAT;
//                    LOG.info("Could not find any SPARQL result format, which is both, accepted and supported. Use " +
//                            "default ({})", DEFAULT_SPARQL_RESULT_FORMAT);
//                }
//                else{
//                    LOG.info("SPARQL result format with highest priority which is both, accepted and supported: {}",
//                            acceptedFormat);
//                }
//            }
//
//            httpVersion = httpRequest.getProtocolVersion();
//		}
//
//		ctx.sendUpstream(me);
//	}

    private static Language getFavouredLanguage(Multimap<Double, String> acceptedMediaTypes){
        Language language;
        for(Double priority : acceptedMediaTypes.keySet()){
            for(String mimeType : acceptedMediaTypes.get(priority)){
                language = Language.getByHttpMimeType(mimeType);
                if(language != null){
                    return language;
                }
            }
        }
        return null;
    }

    private static QueryResultsFormat getFavouredResultsFormat(Multimap<Double, String> acceptedMediaTypes) {
        QueryResultsFormat resultsFormat;
        for(Double priority : acceptedMediaTypes.keySet()){
            for(String mimeType : acceptedMediaTypes.get(priority)){
                resultsFormat = QueryResultsFormat.getByHttpMimeType(mimeType);
                if(resultsFormat != null){
                    return resultsFormat;
                }
            }
        }
        return null;
    }

	@Override
	public void writeRequested(ChannelHandlerContext ctx, final MessageEvent me) throws Exception {
        LOG.debug("Downstream: {}", me.getMessage());

        if(me.getMessage() instanceof HttpResponse){
            ctx.sendDownstream(me);
            return;
        }

        HttpResponse httpResponse;

        String acceptHeader = httpRequest.headers().get(HttpHeaders.Names.ACCEPT);

        // serialize RDF graph in HTTP response
        if (me.getMessage() instanceof ExpiringGraph) {
            Language language = null;
            if (acceptHeader != null) {
                language = getFavouredLanguage(getAcceptedMediaTypes(acceptHeader));
            }
            if (language == null) {
                language = DEFAULT_LANGUAGE;
            }
            httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(), language,
                    ((ExpiringGraph) me.getMessage()));
        }

        // serialize SPARQL results in HTTP response
        else if (me.getMessage() instanceof QueryExecutionResults) {
            QueryResultsFormat resultsFormat = null;
            if (acceptHeader != null) {
                resultsFormat = getFavouredResultsFormat(getAcceptedMediaTypes(acceptHeader));
            }
            if (resultsFormat == null) {
                resultsFormat = DEFAULT_SPARQL_RESULT_FORMAT;
            }

            QueryExecutionResults results = (QueryExecutionResults) me.getMessage();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ResultSetFormatter.output(outputStream, results.getResultSet(), resultsFormat.getResultsFormat());

            // create the HTTP response
            Map<String, String> content = new HashMap<>();
            content.put("results", outputStream.toString());
            content.put("duration", String.valueOf(results.getDuration()));

            httpResponse = HttpResponseFactory.createHttpJsonResponse(httpRequest.getProtocolVersion(), content);
        }

        // some unexpected error (should never happen!)
        else {
            httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, "Some unexpected error occurred!\n\n" +
                            "I know! All errors are unexpected, smart ass :-) ...");
        }

        // send formatted response
        Channels.write(ctx, me.getFuture(), httpResponse, me.getRemoteAddress());
        if(LOG.isInfoEnabled()){
            me.getFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    LOG.info("HTTP response written to {}.", me.getRemoteAddress());
                }
            });
        }
    }

//            if(responseFormatType == RDF){
//                if(me.getMessage() instanceof EmptyAccessResult){
//                    EmptyAccessResult emptyAccessResult = (EmptyAccessResult) me.getMessage();
//                    int codeNumber = emptyAccessResult.getCode().getCodeNumber();
//                    HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(codeNumber);
//                    String content = emptyAccessResult.getMessage();
//                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, responseStatus, content);
//                }
//
//                else if(me.getMessage() instanceof ExpiringGraph){
//                    ExpiringGraph expiringGraph = (ExpiringGraph) me.getMessage();
//                    Language format = (Language) acceptedFormat;
//                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, format, expiringGraph);
//                }
//            }
//
//            else{
//                HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
//                String content = "Format Type for GraphStatusMessage was " + responseFormatType;
//                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, status, content);
//            }
//        }
//
//        else if(me.getMessage() instanceof ResultSet){
//            if(responseFormatType == SPARQL_RESULT){
//                ResultSet resultSet = (ResultSet) me.getMessage();
//                QueryResultsFormat format = (QueryResultsFormat) acceptedFormat;
//
//                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, resultSet, format);
//            }
//
//            else{
//                HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
//                String content = "Format Type for QueryResult was " + responseFormatType;
//                httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, status, content);
//            }
//        }
//
//        else if(me.getMessage() instanceof HttpResponse){
//            httpResponse = (HttpResponse) me.getMessage();
//        }
//
//
//        if(httpResponse == null){
//            HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
//            String content = "Unsupported internal message " + me.getMessage();
//            httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, status, content);
//        }
//
//
//        httpResponse.headers().add("Access-Control-Allow-Origin", "*");
//        httpResponse.headers().add("Access-Control-Allow-Credentials", "true");
//
//        Channels.write(ctx, me.getFuture(), httpResponse, me.getRemoteAddress());
//
//        if(LOG.isInfoEnabled()){
//            me.getFuture().addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture future) throws Exception {
//                    LOG.info("HTTP response written to {}.", me.getRemoteAddress());
//                }
//            });
//        }
//    }


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

            if(LOG.isDebugEnabled()){
                LOG.debug("Media type (from HTTP ACCEPT header): {}.", acceptedMediaType);
                for(int i = 0; i < parts.size(); i++){
                    LOG.debug("Part {}: {}", i + 1, parts.get(i));
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

            LOG.debug("Found accepted media type {} with priority {}.", httpMediaType, priority);

            if(httpMediaType.contains("*")){
                LOG.warn("There is no support for wildcard types ({})", httpMediaType);
                continue;
            }

            result.put(priority * (-1), httpMediaType);
            LOG.debug("Added media type {} with priority {}.", httpMediaType, priority);

        }

        return result;
    }
}

