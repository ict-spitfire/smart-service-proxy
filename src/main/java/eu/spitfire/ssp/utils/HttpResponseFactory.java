package eu.spitfire.ssp.utils;

import com.google.common.collect.Multimap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;

public class HttpResponseFactory {

    public static HttpResponse createHttpErrorResponse(HttpVersion version, HttpResponseStatus status, String content){
        HttpResponse response = new DefaultHttpResponse(version, status);

        String payload = status.getReasonPhrase() + "\n\n" + content;
        ChannelBuffer payloadBuffer = ChannelBuffers.wrappedBuffer(payload.getBytes(Charset.forName("UTF-8")));
        response.setContent(payloadBuffer);

        response.setHeader("Content-Length", payloadBuffer.readableBytes());
        return response;
    }

    public static HttpResponse createHttpErrorResponse(HttpVersion version, HttpResponseStatus status,
                                                       Exception exception){

        //Write exceptions stack trace as payload for error message
        StringWriter errors = new StringWriter();
        exception.printStackTrace(new PrintWriter(errors));

        return createHttpErrorResponse(version, status, errors.toString());
    }

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status,
                                                  Multimap<String, String> headers, ChannelBuffer payload){

        HttpResponse response = new DefaultHttpResponse(version, status);
        response.setContent(payload);

        for(String header : headers.keySet()){
            response.setHeader(header, headers.get(header));
        }

        response.setHeader("Content-Length", payload.readableBytes());

        return response;
    }

}
