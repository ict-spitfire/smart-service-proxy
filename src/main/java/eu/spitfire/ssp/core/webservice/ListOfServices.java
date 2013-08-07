package eu.spitfire.ssp.core.webservice;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.log4j.helpers.LogLog;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.Charset;
import java.util.Set;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 09:51
 * To change this template use File | Settings | File Templates.
 */
public class ListOfServices implements HttpRequestProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Set<URI> services;

    public ListOfServices(Set<URI> services){
        this.services = services;
    }

    private ChannelBuffer getHtmlListOfServices() {
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");
        buf.append("<h2>Available services</h2>\n");
        buf.append("<ul>\n");

        for(URI uri : services){
            buf.append(String.format("<li><a href=\"%s\">", uri));

            String[] uriParts = uri.getPath().split("/");
            if(uriParts.length < 2 || !isUriScheme(uriParts[1]))
                buf.append(String.format("%s</a></li>\n", uri));
            else{
                //Scheme
                String scheme = uriParts[1];

                //Host and path
                String host = null;
                String path = "";
                if(uriParts.length > 2 && InetAddresses.isInetAddress(uriParts[2])){
                    host = uriParts[2];
                    for(int i = 3; i < uriParts.length; i++)
                        path += "/" + uriParts[i];
                }

                try {
                    buf.append(String.format("%s</a></li>\n", new URI(scheme, null, host, -1, path, null, null)));
                }
                catch (URISyntaxException e) {
                    log.error("This should never happen!", e);
                }
            }
        }

        buf.append("</ul>\n");

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
        httpResponse.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
        httpResponse.setContent(getHtmlListOfServices());
        httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());

        responseFuture.set(httpResponse);
    }

    private boolean isUriScheme(String string){
        if("coap".equals(string))
            return true;

        if("file".equals(string))
            return true;

        return false;
    }
}
