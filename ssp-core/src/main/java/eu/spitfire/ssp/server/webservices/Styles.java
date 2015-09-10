package eu.spitfire.ssp.server.webservices;

import eu.spitfire.ssp.server.internal.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 15.05.14.
 */
public class Styles extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(Styles.class.getName());

    public Styles(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
                  String htmlResourcePath) {

        super(ioExecutor, internalTasksExecutor, htmlResourcePath);
    }


    @Override
    public void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
        throws Exception{

        String stylesheetPath = httpRequest.getUri().substring(1);
        log.info("Lookup CSS or JS related file at {}", stylesheetPath);

        ChannelBuffer content = this.readStyleSheet(stylesheetPath);

        String mimeType = stylesheetPath.endsWith(".css") ? "text/css" :
                stylesheetPath.endsWith(".js") ? "text/javascript" :
                        stylesheetPath.endsWith(".png") ? "image/png" : "image/svg+xml";

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, content, mimeType);

        httpResponse.headers().add(HttpHeaders.Names.EXPIRES, HttpResponseFactory.DATE_FORMAT.format(
                System.currentTimeMillis() + HttpResponseFactory.MILLIS_PER_YEAR
        ));

        log.info("Send CSS or JS related file from {}", stylesheetPath);
        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private ChannelBuffer readStyleSheet(String stylesheetPath) throws Exception{
        ChannelBuffer result = ChannelBuffers.dynamicBuffer();
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(stylesheetPath);

        byte[] buffer = new byte[1024];

        int read;
        while((read = inputStream.read(buffer, 0, buffer.length)) != -1){
            result.writeBytes(buffer, 0, read);
        }

        inputStream.close();

        return result;
    }
}
