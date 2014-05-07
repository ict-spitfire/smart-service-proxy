package eu.spitfire.ssp.backends.slse;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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
    private SlseBackendComponentFactory componentFactory;

    private final String slseGraphNamePrefix;

    public HttpVirtualSensorDefinitionWebservice(SlseBackendComponentFactory componentFactory){
        this.componentFactory = componentFactory;
        this.slseGraphNamePrefix = "http://" + this.componentFactory.getSspHostName() + "/virtual-sensor/";
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress){
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
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }


    private void processPost(final Channel channel, final HttpRequest httpRequest,
                             final InetSocketAddress clientAddress) throws Exception{

        SettableFuture<HttpResponse> httpResponseFuture = SettableFuture.create();
        Futures.addCallback(httpResponseFuture, new FutureCallback<HttpResponse>() {

            @Override
            public void onSuccess(HttpResponse httpResponse) {
                writeHttpResponse(channel, httpResponse, clientAddress);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.internalTasksExecutorService);


        Map<String, String> formContent = splitFormContent(httpRequest.getContent());

        if(!formContent.containsKey("form")){
            handleInvalidPostRequest(channel, httpRequest, clientAddress);
        }

        else{
            if("1".equals(formContent.get("form"))){
                handleForm1PostRequest(httpResponseFuture, formContent, httpRequest.getProtocolVersion());
            }

            else if("2".equals(formContent.get("form"))){
                handleForm2PostRequest(channel, httpRequest, clientAddress);
            }

            else{
                handleInvalidPostRequest(channel, httpRequest, clientAddress);
            }
        }
    }


    private void handleForm2PostRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

    }

    private void handleInvalidPostRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {

    }


    private String getErrorHtml(int rows, String errorMessage){
        return "<textarea rows=\"" + rows + "\" cols=\"80\" style=\"color:red\"" +
                "readonly>" + errorMessage + "</textarea>" + "<br>";
    }


    private void handleForm1PostRequest(SettableFuture<HttpResponse> httpResponseFuture,
        final Map<String, String> formContent, HttpVersion httpVersion)
            throws Exception{

        //Check given graph name
        URI graphName = null;
        String graphNameErrorHtml = "";
        if(formContent.containsKey("graphName")){
            try{
                if(formContent.get("graphName").equals(""))
                    graphNameErrorHtml = getErrorHtml(1, "URI missing!");
                else
                    graphName = new URI(formContent.get("graphName"));
            }
            catch(Exception ex){
                log.warn("Malformed URI: {}", ex.getMessage());
                graphNameErrorHtml = getErrorHtml(1, "Malformed URI: " + ex.getMessage());
            }
        }

        //Check given SPARAL query
        Query sparqlQuery = null;
        String sparqlQueryErrorHtml = "";
        if(formContent.containsKey("sparqlQuery")){
            try{
                sparqlQuery = QueryFactory.create(formContent.get("sparqlQuery"));
            }
            catch(Exception ex){
                log.warn("Malformed SPARQL query: {}", ex.getMessage());
                sparqlQueryErrorHtml = getErrorHtml(4, "Malformed SPARQL query: " + ex.getMessage());
            }
        }

        if(graphName == null || sparqlQuery == null){
            ChannelBuffer contentBuffer = getHtmlContent(formContent.get("graphName"), graphNameErrorHtml,
                    formContent.get("sparqlQuery"), sparqlQueryErrorHtml, "");

            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                    HttpResponseStatus.UNPROCESSABLE_ENTITY, contentBuffer, "text/html");

            httpResponseFuture.set(httpResponse);
        }
        else{
            executeSparqlQuery(httpResponseFuture, httpVersion, sparqlQuery, formContent);
        }
    }


    private void executeSparqlQuery(final SettableFuture<HttpResponse> httpResponseFuture,
            final HttpVersion httpVersion, Query sparqlQuery, final Map<String, String> formContent) throws Exception{

        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
        SparqlQueryMessage sparqlQueryMessage = new SparqlQueryMessage(sparqlQuery, sparqlResultFuture);

        Channels.write(this.componentFactory.getLocalChannel(), sparqlQueryMessage);

        Futures.addCallback(sparqlResultFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                HttpResponse httpResponse;

                try{

                    String sensorValue = createVirtualSensorExamplePayload(formContent.get("graphName"),
                            resultSet);

                    ChannelBuffer contentBuffer = getHtmlContent(formContent.get("graphName"), "",
                            formContent.get("sparqlQuery"), "", sensorValue);

                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.UNPROCESSABLE_ENTITY, contentBuffer, "text/html");
                }

                catch (Exception e) {
                    log.error("This should never happen.", e);
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }

                httpResponseFuture.set(httpResponse);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponse httpResponse;

                try{
                    ChannelBuffer contentBuffer = getHtmlContent(formContent.get("graphName"), "",
                            formContent.get("sparqlQuery"), "", "ERROR!!!\n\n" + t.getMessage());

                   httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                           HttpResponseStatus.UNPROCESSABLE_ENTITY, contentBuffer, "text/html");
                }

                catch (Exception e) {
                    log.error("This should never happen.", e);
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }

                httpResponseFuture.set(httpResponse);
            }
        });


    }


    private String createVirtualSensorExamplePayload(String virtualSensorUriPath, ResultSet resultSet){
        Model model = ModelFactory.createDefaultModel();

        //Get the first row of the result set
        QuerySolution querySolution = resultSet.nextSolution();
        RDFNode sensorValue = querySolution.get(querySolution.varNames().next());

        Statement statement  = model.createStatement(
                model.createResource(slseGraphNamePrefix + virtualSensorUriPath),
                model.createProperty("http://spitfire-project.eu/ontology/ns/value"),
                sensorValue);

        model.add(statement);

        StringWriter writer = new StringWriter();
        model.write(writer, "N3");

        return writer.toString();
    }



    private Map<String, String> splitFormContent(ChannelBuffer contentBuffer) throws UnsupportedEncodingException {
        String content = URLDecoder.decode(contentBuffer.toString(Charset.forName("UTF-8")), "UTF-8");
        Map<String, String> params = new HashMap<>();

        for(String param : content.split("&")){
            String[] tmp = param.split("=");
            params.put(tmp[0], tmp.length > 1 ? tmp[1] : "");
        }

        return params;
    }


    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        String queryExample = "SELECT (avg(?value) AS ?aggValue) WHERE {\n" +
                "  ?s\n    <http://spitfire-project.eu/ontology/ns/obs>\n" +
                "      <http://localhost:8182/ld4s/resource/property/temperatur> .\n" +
                "  ?s\n    <http://spitfire-project.eu/ontology/ns/value>\n" +
                "      ?value .\n}";

        ChannelBuffer htmlContentBuffer = this.getHtmlContent("1", "", queryExample, "", "");

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, htmlContentBuffer, "text/html");

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private ChannelBuffer getHtmlContent(String graphName, String graphNameError, String sparqlQuery,
                                         String sparqlQueryError, String virtualSensorValue) throws Exception{

        InputStream inputStream = this.getClass().getResourceAsStream("VirtualSensorDefinition.html");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder formatStringBuilder = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            formatStringBuilder.append(line);
            formatStringBuilder.append("\n");
            line = reader.readLine();
        }

        String content = String.format(formatStringBuilder.toString(), this.slseGraphNamePrefix, graphName,
                graphNameError, sparqlQuery, sparqlQueryError, virtualSensorValue);

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
