package eu.spitfire.ssp.core.webservice;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 13:18
 * To change this template use File | Settings | File Templates.
 */
public class FaviconHttpRequestProcessor implements DefaultHttpRequestProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private ChannelBuffer faviconBuffer;
    private int faviconBufferLength;

    public FaviconHttpRequestProcessor(){
        try{
            InputStream inputStream = FaviconHttpRequestProcessor.class.getResourceAsStream("favicon.ico");
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

    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
            httpResponse.setContent(ChannelBuffers.copiedBuffer(faviconBuffer));
            httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "image/x-icon");
            httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, faviconBufferLength);

            responseFuture.set(httpResponse);
    }
}
