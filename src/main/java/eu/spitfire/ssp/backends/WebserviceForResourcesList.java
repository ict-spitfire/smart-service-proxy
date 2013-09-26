package eu.spitfire.ssp.backends;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.server.webservices.DefaultHttpRequestProcessor;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 17.09.13
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public abstract class WebserviceForResourcesList<T> implements DefaultHttpRequestProcessor {

    private Map<URI, T> resources;
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public WebserviceForResourcesList(Map<URI, T> resources) throws ConfigurationException {
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
            type= resources.values().iterator().next().getClass().toString();
        } catch(NoSuchElementException e){
            type = "none";
        }

        buf.append(String.format("<h2>Type of data origin: %s</h2>\n", type));

        Multimap<T, URI> dataOrigins = HashMultimap.create();

        for(URI resourceUri : resources.keySet()){
            dataOrigins.put(resources.get(resourceUri), resourceUri);
        }

        for(T dataOrigin : dataOrigins.keySet()){
            buf.append(String.format("<h3>Resources from Data Origin: %s</h3>\n", dataOrigin));
            buf.append("<ul>\n");
            Collection<URI> resourcesFromDataOrigin = dataOrigins.get(dataOrigin);
            for(URI resourceUri : resourcesFromDataOrigin){
                try{
                    String query = "uri=" + resourceUri;
                    URI link = new URI("http", null, Main.SSP_DNS_NAME,
                            Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT , "/", query, null);
                    buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", link, resourceUri));
                } catch (URISyntaxException e) {
                    log.error("This should never happen.", e);
                }

            }
            buf.append("</ul>\n");
        }

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }

}
