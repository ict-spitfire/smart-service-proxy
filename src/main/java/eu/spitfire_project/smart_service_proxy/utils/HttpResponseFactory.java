package eu.spitfire_project.smart_service_proxy.utils;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.nio.charset.Charset;

public class HttpResponseFactory {

    public static HttpResponse createHttpResponse(HttpVersion version, HttpResponseStatus status){
        HttpResponse response = new DefaultHttpResponse(version, status);

        byte[] content = status.toString().getBytes(Charset.forName("UTF-8"));
        response.setContent(ChannelBuffers.wrappedBuffer(content));

        return response;
    }

}
