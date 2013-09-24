package eu.spitfire.ssp.backends.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.webservices.DefaultHttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public abstract class ServiceToListResourcesPerDataOrigin<T> implements DefaultHttpRequestProcessor {

    private Map<URI, T> resources;

    public ServiceToListResourcesPerDataOrigin(Map<URI, T> resources){
        this.resources = resources;
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK);

        ChannelBuffer content = getHtmlContent();


        httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");

        httpResponse.setContent(content);

        responseFuture.set(httpResponse);
    }

    private ChannelBuffer getHtmlContent(){
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");

        String type;
        try{
            type= resources.values().iterator().next().toString();
        } catch(NullPointerException e){
            type = "none";
        }

        buf.append(String.format("<h2>Type of data origin: %s</h2>\n", type));

        Multimap<T, URI> dataOrigins = HashMultimap.create();

        for(URI resourceUri : resources.keySet()){
            dataOrigins.put(resources.get(resourceUri), resourceUri);
        }

        for(T dataOrigin : dataOrigins.keySet()){
            buf.append(String.format("<h3>%s</h3>\n", dataOrigin));
            buf.append("<ul>\n");
            Collection<URI> resourcesFromDataOrigin = dataOrigins.get(dataOrigin);
            for(URI resourceUri : resourcesFromDataOrigin){
                buf.append(String.format("<li>%s</li>\n", resourceUri));
            }
            buf.append("</ul>\n");
        }

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }

}
