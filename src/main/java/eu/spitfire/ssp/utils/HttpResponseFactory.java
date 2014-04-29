package eu.spitfire.ssp.utils;

import com.google.common.collect.Multimap;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.messages.*;
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
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

public class HttpResponseFactory {

    private static Logger log = LoggerFactory.getLogger(HttpResponseFactory.class.getName());

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    static{
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static final long MILLIS_PER_YEAR = 31536000730L;

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status, String content){
        HttpResponse response = new DefaultHttpResponse(version, status);

        String payload = status.getReasonPhrase() + "\n\n" + content;
        ChannelBuffer payloadBuffer = ChannelBuffers.wrappedBuffer(payload.getBytes(Charset.forName("UTF-8")));
        response.setContent(payloadBuffer);
        response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payloadBuffer.readableBytes());
        return response;
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

    public static HttpResponse createHttpResponse(HttpVersion httpVersion, QueryResultMessage queryResultMessage,
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
                    dateFormat.format(new Date(System.currentTimeMillis() + MILLIS_PER_YEAR)));
        else
            httpResponse.headers().add(HttpHeaders.Names.EXPIRES,
                    dateFormat.format(graphStatusMessage.getExpiringGraph().getExpiry()));

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
