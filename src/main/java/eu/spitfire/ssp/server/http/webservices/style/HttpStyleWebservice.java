package eu.spitfire.ssp.server.http.webservices.style;

import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 * Created by olli on 15.05.14.
 */
public class HttpStyleWebservice extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(HttpStyleWebservice.class.getName());

    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
        throws Exception{

        String stylesheetPath = httpRequest.getUri().substring(1);
        log.debug("Lookup stylesheet at {}", stylesheetPath);

        ChannelBuffer content = this.readStyleSheet(stylesheetPath);

        String mimeType = stylesheetPath.endsWith(".css") ? "text/css" :
                stylesheetPath.endsWith(".js") ? "text/javascript" :
                        stylesheetPath.endsWith(".png") ? "image/png" : "image/svg+xml";
        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, content, mimeType);

        writeHttpResponse(channel, httpResponse, clientAddress);
    }

    private ChannelBuffer readStyleSheet(String stylesheetPath) throws Exception{
        ChannelBuffer result = ChannelBuffers.dynamicBuffer();
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(stylesheetPath);

        int value = inputStream.read();
        while(value != -1){
            result.writeByte(value);
            value = inputStream.read();
        }

        return result;
    }
}
