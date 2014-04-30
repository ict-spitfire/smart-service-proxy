package eu.spitfire.ssp.server.http.webservices;

import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 * Created by olli on 30.04.14.
 */
public class HttpSlseDefinitionWebservice extends HttpWebservice {

    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

        if(httpRequest.getMethod() == HttpMethod.GET){
            processGet(channel, httpRequest, clientAddress);
        }

        else{
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.METHOD_NOT_ALLOWED, "Method " + httpRequest.getMethod() + " not allowed!");

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
        try{
            ChannelBuffer htmlContentBuffer = this.getHtmlContent();

            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.OK, htmlContentBuffer, "text/html");

            writeHttpResponse(channel, httpResponse, clientAddress);

        }
        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private ChannelBuffer getHtmlContent() throws Exception{
        InputStream inputStream = FaviconHttpWebservice.class.getResourceAsStream("SlseDefinition.html");
        ChannelBuffer htmlContentBuffer = ChannelBuffers.dynamicBuffer();

        int value = inputStream.read();
        while(value != -1){
            htmlContentBuffer.writeByte(value);
            value = inputStream.read();
        }

        return htmlContentBuffer;
    }
}
