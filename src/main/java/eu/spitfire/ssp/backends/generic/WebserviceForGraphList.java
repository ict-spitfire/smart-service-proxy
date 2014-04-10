package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.webservices.HttpNonSemanticWebservice;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public class WebserviceForGraphList<T> implements HttpNonSemanticWebservice {

    private String backendName;
    private Collection<DataOrigin<T>> dataOrigins;
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    WebserviceForGraphList(String backendName, Collection<DataOrigin<T>> dataOrigins){
        this.backendName = backendName;
        this.dataOrigins = dataOrigins;
    }


    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK);

        ChannelBuffer content = getHtmlContent();

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
        httpResponse.setContent(content);

        responseFuture.set(httpResponse);
    }

    private ChannelBuffer getHtmlContent(){
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");

        buf.append(String.format("<h2>Data Origins registered at backend: %s</h2>\n", this.backendName));

        buf.append("<ol>\n");
        for(DataOrigin<T> dataOrigin : dataOrigins){
            buf.append("\t<li>\n");
            buf.append("\t\t").append(String.format("Data Origin:\t%s", dataOrigin.getIdentifier())).append("\n");
            buf.append("\t\t").append(String.format("Graph Name:\t%s", dataOrigin.getGraphName())).append("\n");
            buf.append("\t</li>\n");
        }
        buf.append("</ol>");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }

}
