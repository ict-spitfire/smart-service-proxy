package eu.spitfire.ssp.server.http.webservices;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * This is the {@link HttpWebservice} instance to provide the /favicon.ico service
 *
 * @author Oliver Kleine
 */
public class Favicon extends HttpWebservice {

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    private static final long MILLISECONDS_PER_YEAR = 31556926000L;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private ChannelBuffer faviconBuffer;
    private int faviconBufferLength;

    /**
     * Reads the favicon.ico file at <code>resources/eu/spitfire/ssp/core/webservice/favicon.ico</code> and provides
     * this image as favicon service.
     */
    public Favicon(ExecutorService ioExecutor){

        super(ioExecutor, null, null);

        try{
            InputStream inputStream = Favicon.class.getResourceAsStream("favicon.ico");
            faviconBuffer = ChannelBuffers.dynamicBuffer();
            int value = inputStream.read();
            while(value != -1){
                faviconBuffer.writeByte(value);
                value = inputStream.read();
            }

            faviconBufferLength = faviconBuffer.readableBytes();

        }
        catch(Exception e){
            log.error("Error while creating favicon resource", e);
        }
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
        log.debug("Received HTTP request for favicon.ico");

        //Create HTTP response
        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
        httpResponse.setContent(ChannelBuffers.copiedBuffer(faviconBuffer));
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "image/x-icon");
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, faviconBufferLength);

        String expires = dateFormat.format(new Date(System.currentTimeMillis() + MILLISECONDS_PER_YEAR));
        httpResponse.headers().add(HttpHeaders.Names.EXPIRES, expires);

        writeHttpResponse(channel, httpResponse, clientAddress);
    }
}
