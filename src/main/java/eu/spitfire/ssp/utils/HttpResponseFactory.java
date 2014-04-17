package eu.spitfire.ssp.utils;

import com.google.common.collect.Multimap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class HttpResponseFactory {

    private static Logger log = LoggerFactory.getLogger(HttpResponseFactory.class.getName());

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


    private static void setHeaders(HttpMessage httpMessage, Multimap<String, String> headers){
        for(String headerName : headers.keySet()){
            Iterable<String> headerValue = headers.get(headerName);
            httpMessage.headers().add(headerName, headerValue);
            log.debug("Set Header: {} (Name), {} (Value(s))", headerName, headerValue);
        }
    }
}
