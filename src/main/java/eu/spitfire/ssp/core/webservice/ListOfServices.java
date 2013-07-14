package eu.spitfire.ssp.core.webservice;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.net.URI;
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

    private Set<URI> services;

    public ListOfServices(Set<URI> services){
        this.services = services;
    }

    private ChannelBuffer getHtmlListOfServices(){
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");
        buf.append("<h2>Entities</h2>\n");
        buf.append("<ul>\n");

        for(URI uri : services){
            buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri));
        }

//        for(Map.Entry<URI, AbstractGatewayFactory> entry: httpRequestProcessors.entrySet()){
//            buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", entry.getKey(), entry.getKey()));
//        }
        buf.append("</ul>\n");

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString()
                .getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
        httpResponse.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
        httpResponse.setContent(getHtmlListOfServices());
        httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());

        responseFuture.set(httpResponse);
    }
}
