package eu.spitfire.ssp.server.http.webservices;

import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

/**
 * Created by olli on 02.05.14.
 */
public class HttpSlseCreationWebservice extends HttpWebservice{

    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

        if(httpRequest.getMethod() == HttpMethod.POST){
            processPost(channel, httpRequest, clientAddress);
        }

        else{
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.METHOD_NOT_ALLOWED, "Only HTTP POST  Requests are allowed!");

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private void processPost(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

        ChannelBuffer contentBuffer = httpRequest.getContent();
        String content = contentBuffer.toString(Charset.forName("UTF-8"));

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, content);

        writeHttpResponse(channel, httpResponse, clientAddress);
    }
}
