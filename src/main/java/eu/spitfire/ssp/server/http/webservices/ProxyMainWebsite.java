package eu.spitfire.ssp.server.http.webservices;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * Provides a list of all registered services on this proxy server.
 *
 * @author Oliver Kleine
 */
public class ProxyMainWebsite extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Map<String, HttpWebservice> services;

    /**
     * @param webservices the {@link Set} containing the {@link URI}s to be listed in the HTTP response
     */
    public ProxyMainWebsite(Map<String, HttpWebservice> webservices){
        this.services = webservices;
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
        log.debug("Received HTTP request for list of available services!");

        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
        httpResponse.headers().add(CONTENT_TYPE, "text/html; charset=utf-8");

        ChannelBuffer payload = getHtmlListOfServices();
        httpResponse.headers().add(CONTENT_LENGTH, payload.readableBytes());
        httpResponse.setContent(payload);

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private ChannelBuffer getHtmlListOfServices() {

        Map<String, StringBuilder> semanticServices = new TreeMap<>();

//        StringBuilder semanticServices = new StringBuilder();
        StringBuilder otherServices = new StringBuilder();

        for(String uri : services.keySet()){
            HttpWebservice httpWebservice = services.get(uri);

            if(httpWebservice instanceof HttpSemanticProxyWebservice){
                HttpSemanticProxyWebservice semanticProxyWebservice = (HttpSemanticProxyWebservice) httpWebservice;
                String backendName = semanticProxyWebservice.getBackendName();
                String nextGraph = String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri);

                if(semanticServices.containsKey(backendName)){
                    semanticServices.get(backendName).append(nextGraph);
                }

                else{
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(nextGraph);

                    semanticServices.put(backendName, stringBuilder);
                }

            }
            else{
                if(!("/".equals(uri) || "/favicon.ico".equals(uri))){
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

        for(String backendName : semanticServices.keySet()){
            buf.append(String.format("<h3>Backend: %s</h3>\n", backendName));
            buf.append("<ul>\n");
            buf.append(semanticServices.get(backendName));
            buf.append("</ul>\n");
        }

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }
}
