package eu.spitfire.ssp.backends.slse;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryException;
import com.hp.hpl.jena.query.QueryFactory;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 05.05.14.
 */
public class HttpVirtualSensorDefinitionWebservice extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private SlseRegistry slseRegistry;


    public HttpVirtualSensorDefinitionWebservice(SlseRegistry slseRegistry){
        this.slseRegistry = slseRegistry;
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

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


    private void processPost(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
        Map<String, String> params = new HashMap<>();
        HttpResponse httpResponse;

        try{

            String content = httpRequest.getContent().toString(Charset.forName("UTF-8"));

            log.info("Received content: {}", content);

            for(String param : content.split("&")){
                String[] tmp = param.split("=");
                params.put(tmp[0], tmp.length > 1 ? tmp[1] : "");
            }

            String sGraphName = null;
            String graphNameError = "&nbsp;";
            String sSparqlQuery = null;
            String sparqlQueryError = "&nbsp;";

            try{
                sGraphName = URLDecoder.decode(params.get("graphName"), "UTF-8");
                log.info("Graph Name: {}", sGraphName);

                URI graphName = new URI(sGraphName);
            }
            catch(Exception ex){
                graphNameError = "Malformed URI: " + ex.getMessage();
            }


            try{
                sSparqlQuery = URLDecoder.decode(params.get("sparqlQuery"), "UTF-8");
                Query sparqlQuery = QueryFactory.create(sSparqlQuery);
            }
            catch(QueryException ex){
                sparqlQueryError = "Malformed SPARQL query: " + ex.getMessage();
            }

            if(sGraphName == null){
                graphNameError = "There was no graph name specified!";
            }

            if(sSparqlQuery == null){
                sparqlQueryError = "There was no SPARQL query specified!";
            }


            ChannelBuffer contentBuffer = getHtmlContent(sGraphName, graphNameError, sSparqlQuery, sparqlQueryError,
                    "aggr. query...", "aggr. query result");

            httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.OK, contentBuffer, "text/html");

        }

        catch(Exception ex){
            log.error("This should never happen.", ex);
            httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

        }

        writeHttpResponse(channel, httpResponse, clientAddress);

    }

    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
        try{
            ChannelBuffer htmlContentBuffer = this.getHtmlContent("", "", "", "", "", "");

            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.OK, htmlContentBuffer, "text/html");

            writeHttpResponse(channel, httpResponse, clientAddress);

        }
        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private ChannelBuffer getHtmlContent(String graphName, String graphNameError, String sparqlQuery, String sparqlQueryError,
                           String aggrSparqlQuery, String virtualSensorValue) throws Exception{

        InputStream inputStream = this.getClass().getResourceAsStream("VirtualSensorDefinition.html");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder formatStringBuilder = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            formatStringBuilder.append(line);
            formatStringBuilder.append("\n");
            line = reader.readLine();
        }

        String content = String.format(formatStringBuilder.toString(), graphName, graphNameError, sparqlQuery,
                sparqlQueryError, aggrSparqlQuery, virtualSensorValue);

        return ChannelBuffers.wrappedBuffer(content.getBytes(Charset.forName("UTF-8")));
    }


//    private ChannelBuffer getHtmlContent() throws Exception{
//        InputStream inputStream = HttpFaviconWebservice.class.getResourceAsStream("VirtualSensorDefinition.html");
//
//        ChannelBuffer htmlContentBuffer = ChannelBuffers.dynamicBuffer();
//
//        int value = inputStream.read();
//        while(value != -1){
//            htmlContentBuffer.writeByte(value);
//            value = inputStream.read();
//        }
//
//        return htmlContentBuffer;
//    }
}
