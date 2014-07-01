package eu.spitfire.ssp.backends.slse.webservice;

import com.google.common.util.concurrent.ListenableFuture;
import com.hp.hpl.jena.query.QueryFactory;
import de.uniluebeck.itm.xsd.slse.jaxb.SemanticEntity;
import eu.spitfire.ssp.backends.slse.SlseDataOrigin;
import eu.spitfire.ssp.backends.slse.SlseRegistry;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Created by olli on 30.06.14.
 */
public abstract class HttpAbstractVirtualSensorCreator extends HttpWebservice{

    private static Logger log = LoggerFactory.getLogger(HttpAbstractVirtualSensorCreator.class.getName());

    private SlseRegistry slseRegistry;
    private String graphNamePrefix;
    private String htmlPath;


    protected HttpAbstractVirtualSensorCreator(SlseRegistry slseRegistry, String graphNamePrefix, String htmlPath){
        this.slseRegistry = slseRegistry;
        this.graphNamePrefix = graphNamePrefix;
        this.htmlPath = htmlPath;
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception {
        try{
            if(httpRequest.getMethod() == HttpMethod.GET){
                processGet(channel, httpRequest, clientAddress);
            }

            else if(httpRequest.getMethod() == HttpMethod.POST){
                processPost(channel, httpRequest, clientAddress);
            }

            else{
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED, "Method " + httpRequest.getMethod() + " not allowed!");

                writeHttpResponse(channel, httpResponse, clientAddress);
            }
        }
        catch(Exception ex){
            log.error("Internal Server Error because of exception!", ex);
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    /**
     * Returns the fully qualified name of a graph with the given path
     *
     * @param uriPath the path of the graph name
     *
     * @return the fully qualified name of a graph with the given path
     *
     * @throws URISyntaxException
     */
    protected URI getGraphName(String uriPath) throws URISyntaxException {
        return new URI(graphNamePrefix + uriPath);
    }


    protected ListenableFuture<Void> register(SlseDataOrigin dataOrigin) throws Exception{
        return this.slseRegistry.registerSlseDataOrigin(dataOrigin);
    }


    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, getHtmlContent(), "text/html");

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    protected abstract void processPost(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception;


    private ChannelBuffer getHtmlContent() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(htmlPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder content = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            content.append(line);
            content.append("\n");
            line = reader.readLine();
        }

        return ChannelBuffers.wrappedBuffer(content.toString().getBytes(Charset.forName("UTF-8")));
    }

}
