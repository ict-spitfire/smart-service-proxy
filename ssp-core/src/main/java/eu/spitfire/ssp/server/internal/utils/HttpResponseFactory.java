package eu.spitfire.ssp.server.internal.utils;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

public class HttpResponseFactory {

    private static Logger LOG = LoggerFactory.getLogger(HttpResponseFactory.class.getName());
    private static Gson GSON = new Gson();

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    static{
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static final long MILLIS_PER_YEAR = 31536000730L;


    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status, String content){
        HttpResponse response = new DefaultHttpResponse(version, status);
        ChannelBuffer payloadBuffer = ChannelBuffers.wrappedBuffer(content.getBytes(Charset.forName("ISO-8859-1")));
        response.setContent(payloadBuffer);
        response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payloadBuffer.readableBytes());
        return response;
    }


    public static HttpResponse createHttpJsonResponse(HttpVersion version, Map<String, String> content){
        HttpResponse httpResponse = new DefaultHttpResponse(version, HttpResponseStatus.OK);

        ChannelBuffer payload = ChannelBuffers.wrappedBuffer(GSON.toJson(content).getBytes(Charset.forName("UTF-8")));
        httpResponse.setContent(payload);
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());

        httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/json; charset=UTF-8");

        return httpResponse;

    }

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status,
                                                  ChannelBuffer content, String contentType){

        HttpResponse httpResponse = new DefaultHttpResponse(version, status);
        httpResponse.setContent(content);

        httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);

        return httpResponse;
    }

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status,
                                                  Throwable throwable){

        //Write exceptions stack trace as payload for error message
        StringWriter errors = new StringWriter();
        throwable.printStackTrace(new PrintWriter(errors));

        return createHttpResponse(version, status, errors.toString());
    }


    public static HttpResponse createHttpResponse(HttpVersion version, QueryResultsFormat queryResultsFormat,
            ResultSet resultSet){

        ChannelBuffer payload = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream outputStream = new ChannelBufferOutputStream(payload);

        ResultSetFormatter.output(outputStream, resultSet, queryResultsFormat.getResultsFormat());

        HttpResponse httpResponse = new DefaultHttpResponse(version, HttpResponseStatus.OK);
        httpResponse.setContent(payload);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE,
            queryResultsFormat.getMimeType()  + "; charset=ISO-8859-1");
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());

        return httpResponse;

    }


    public static HttpResponse createHttpResponse(HttpVersion version, Language language, ExpiringGraph expiringGraph){

        Model model = expiringGraph.getModel();
        StringWriter writer = new StringWriter();

        //model.write(writer, language.getRdfFormat().getLang().getName());
        //RDFDataMgr.write(writer, model, language.getRdfFormat().getLang());
        RDFDataMgr.write(writer, model, Lang.TTL);

        LOG.info("Model to be serialized{}", model);

        HttpResponse httpResponse = new DefaultHttpResponse(version, OK);
        byte[] content = writer.toString().getBytes(Charset.forName("UTF-8"));
        ChannelBuffer payload = ChannelBuffers.wrappedBuffer(content);
        httpResponse.setContent(payload);

        LOG.error("\n{}", new String(content, Charset.forName("UTF-8")));

        //Set HTTP response headers
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, language.getMimeType() + "; charset=UTF-8");
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());
        httpResponse.headers().add(HttpHeaders.Names.EXPIRES, DATE_FORMAT.format(expiringGraph.getExpiry()));
        httpResponse.headers().add(HttpHeaders.Names.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
//        httpResponse.headers().add("Access-Control-Allow-Origin", "*");
//        httpResponse.headers().add("Access-Control-Allow-Credentials", "true");

        return httpResponse;
    }


    private static void setHeaders(HttpMessage httpMessage, Multimap<String, String> headers){
        for(String headerName : headers.keySet()){
            Iterable<String> headerValue = headers.get(headerName);
            httpMessage.headers().add(headerName, headerValue);
            LOG.debug("Set Header: {} (Name), {} (Value(s))", headerName, headerValue);
        }
    }
}
