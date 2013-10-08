package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * Provides a list of all registered services on this proxy server.
 *
 * @author Oliver Kleine
 */
public class ProxyMainWebsite implements DefaultHttpRequestProcessor{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Map<URI, HttpRequestProcessor> services;

    /**
     * @param webservices the {@link Set} containing the {@link URI}s to be listed in the HTTP response
     */
    public ProxyMainWebsite(Map<URI, HttpRequestProcessor> webservices){
        this.services = webservices;
    }

    private ChannelBuffer getHtmlListOfServices() {

        StringBuilder semanticServices = new StringBuilder();
        StringBuilder otherServices = new StringBuilder();

        for(URI uri : services.keySet()){
            HttpRequestProcessor httpRequestProcessor = services.get(uri);
            if(httpRequestProcessor instanceof SemanticHttpRequestProcessor){
                semanticServices.append(String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri));
            }
            else{
                if(!("/".equals(uri.getPath()) || "/favicon.ico".equals(uri.getPath()) ||
                        "/sparql".equals(uri.getPath()))){
                    otherServices.append(String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri));
                }
            }
        }

        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");
        buf.append("<h2>Info and Administration Services</h2>\n");
        buf.append("<ul>\n");
        buf.append(otherServices.toString());
        buf.append("</ul>\n");
        buf.append("<h2>Proxy Service URIs for registered Semantic Resources</h2>");
        buf.append("<ul>\n");
        buf.append(semanticServices.toString());
        buf.append("</ul>\n");
        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        log.debug("Received HTTP request for list of available services!");

        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
        httpResponse.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
        httpResponse.setContent(getHtmlListOfServices());
        httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());

        responseFuture.set(httpResponse);
    }
}
