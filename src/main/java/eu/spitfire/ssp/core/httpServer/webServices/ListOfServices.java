package eu.spitfire.ssp.core.httpServer.webServices;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.core.UIElement;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.TreeSet;

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

    @Override
    public HttpResponse processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {

    }

    private ChannelBuffer getHtmlListOfServices(){
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");
        buf.append("<h2>Entities</h2>\n");
        buf.append("<ul>\n");

        TreeSet<URI> entitySet = new TreeSet<URI>(httpRequestProcessors.keySet());
        for(URI uri : entitySet){
            buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri));
        }

//        for(Map.Entry<URI, AbstractGateway> entry: httpRequestProcessors.entrySet()){
//            buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", entry.getKey(), entry.getKey()));
//        }
        buf.append("</ul>\n");

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString()
                .getBytes(Charset.forName("UTF-8")));
    }
}
