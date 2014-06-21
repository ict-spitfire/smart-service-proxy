package eu.spitfire.ssp.server.http.webservices;

import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 * Created by olli on 15.05.14.
 */
public class HttpStylesheetWebservice extends HttpWebservice {

    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
        throws Exception{

        ChannelBuffer content = this.readStyleSheet();
        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, content, "text/css");

        writeHttpResponse(channel, httpResponse, clientAddress);
    }

    private ChannelBuffer readStyleSheet() throws Exception{
        ChannelBuffer result = ChannelBuffers.dynamicBuffer();
        InputStream inputStream = HttpFaviconWebservice.class.getResourceAsStream("style.css");

        int value = inputStream.read();
        while(value != -1){
            result.writeByte(value);
            value = inputStream.read();
        }

        return result;
    }
}
