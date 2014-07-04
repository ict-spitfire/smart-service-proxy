package eu.spitfire.ssp.server.http;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.common.messages.EmptyGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.ExpiringGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryResultMessage;
import eu.spitfire.ssp.utils.Language;
import eu.spitfire.ssp.utils.SparqlResultFormat;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

public class HttpResponseFactory {

    private static Logger log = LoggerFactory.getLogger(HttpResponseFactory.class.getName());
    private static Gson gson = new Gson();

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    static{
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static final long MILLIS_PER_YEAR = 31536000730L;

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status, String content){
        HttpResponse response = new DefaultHttpResponse(version, status);

        String payload = "";
//        if(!status.equals(HttpResponseStatus.OK)){
//            payload += status.getReasonPhrase() + "\n\n";
//        }
        payload += content;

        ChannelBuffer payloadBuffer = ChannelBuffers.wrappedBuffer(payload.getBytes(Charset.forName("UTF-8")));
        response.setContent(payloadBuffer);
        response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payloadBuffer.readableBytes());
        return response;
    }


    public static HttpResponse createHttpJsonResponse(HttpVersion httpVersion, Map<String, String> content){
        HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, HttpResponseStatus.OK);

        ChannelBuffer payload = ChannelBuffers.wrappedBuffer(gson.toJson(content).getBytes(Charset.forName("UTF-8")));
        httpResponse.setContent(payload);
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());

        httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/json");

        return httpResponse;

    }

    public static HttpResponse createHttpResponse(HttpVersion httpVersion, HttpResponseStatus status,
                                                  ChannelBuffer content, String contentType){

        HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, status);
        httpResponse.setContent(content);

        httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);

        return httpResponse;
    }

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status,
                                                  Exception exception){

        //Write exceptions stack trace as payload for error message
        StringWriter errors = new StringWriter();
        exception.printStackTrace(new PrintWriter(errors));

        return createHttpResponse(version, status, errors.toString());
    }

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status,
                                                  Multimap<String, String> headers, ChannelBuffer payload){

        HttpResponse response = new DefaultHttpResponse(version, status);
        setHeaders(response, headers);

        response.setContent(payload);
        response.headers().add("Content-Length", payload.readableBytes());
        return response;
    }

    public static HttpResponse createHttpResponse(HttpVersion httpVersion, SparqlQueryResultMessage queryResultMessage,
                                                  SparqlResultFormat sparqlResultFormat){

        ChannelBuffer payload = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream outputStream = new ChannelBufferOutputStream(payload);

        ResultSetFormatter.output(outputStream, queryResultMessage.getQueryResult(), sparqlResultFormat.resultsFormat);

        HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, HttpResponseStatus.OK);
        httpResponse.setContent(payload);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, sparqlResultFormat.mimeType);
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());

        return httpResponse;

    }

    public static HttpResponse createHttpResponse(HttpVersion httpVersion, EmptyGraphStatusMessage graphStatusMessage){

        return HttpResponseFactory.createHttpResponse(httpVersion, graphStatusMessage.getStatusCode(),
                "The operation returned with code: " + graphStatusMessage.getStatusCode().toString());
    }


    public static HttpResponse createHttpResponse(HttpVersion httpVersion,
                                                  GraphStatusErrorMessage graphStatusErrorMessage){

        return HttpResponseFactory.createHttpResponse(httpVersion, graphStatusErrorMessage.getStatusCode(),
                graphStatusErrorMessage.getErrorMessage());
    }


    public static HttpResponse createHttpResponse(HttpVersion httpVersion, Language language,
                                                  ExpiringGraphStatusMessage graphStatusMessage){

        Model model = graphStatusMessage.getExpiringGraph().getGraph();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        log.info("Model to be serialized{}", model);

        //Serialize the model associated with the resource and write on OutputStream
        model.write(byteArrayOutputStream, language.lang);

        HttpResponse httpResponse = new DefaultHttpResponse(httpVersion, OK);

        ChannelBuffer payload = ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray());
        httpResponse.setContent(payload);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, language.mimeType);
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());

        if(graphStatusMessage.getExpiringGraph().getExpiry() == null)
            httpResponse.headers().add(HttpHeaders.Names.EXPIRES,
                    DATE_FORMAT.format(new Date(System.currentTimeMillis() + MILLIS_PER_YEAR)));
        else
            httpResponse.headers().add(HttpHeaders.Names.EXPIRES,
                    DATE_FORMAT.format(graphStatusMessage.getExpiringGraph().getExpiry()));

        httpResponse.headers().add(HttpHeaders.Names.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        httpResponse.headers().add("Access-Control-Allow-Origin", "*");
        httpResponse.headers().add("Access-Control-Allow-Credentials", "true");

        return httpResponse;
    }


    private static void setHeaders(HttpMessage httpMessage, Multimap<String, String> headers){
        for(String headerName : headers.keySet()){
            Iterable<String> headerValue = headers.get(headerName);
            httpMessage.headers().add(headerName, headerValue);
            log.debug("Set Header: {} (Name), {} (Value(s))", headerName, headerValue);
        }
    }
}
