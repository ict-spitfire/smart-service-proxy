package eu.spitfire.ssp.backends.slse.webservice;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.slse.SlseBackendComponentFactory;
import eu.spitfire.ssp.backends.slse.SlseDataOrigin;
import eu.spitfire.ssp.backends.slse.SlseRegistry;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.exceptions.GraphNameAlreadyExistsException;
import eu.spitfire.ssp.utils.exceptions.IdentifierAlreadyRegisteredException;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 05.05.14.
 */
public class HttpVirtualSensorsCreatorOld extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private static String SSP_HOST;
    private static int SSP_PORT;
    private static final String EMPTY = "&nbsp;";
    private static String SLSE_GRAPH_NAME_PREFIX;

    private LocalServerChannel localChannel;
    private SlseRegistry slseRegistry;


    public HttpVirtualSensorsCreatorOld(SlseBackendComponentFactory componentFactory){
        SSP_HOST = componentFactory.getSspHostName();
        SSP_PORT = componentFactory.getSspPort();
        SLSE_GRAPH_NAME_PREFIX = "http://" + SSP_HOST + "/virtual-sensor/";

        this.localChannel = componentFactory.getLocalChannel();
        this.slseRegistry = (SlseRegistry) componentFactory.getDataOriginRegistry();
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


    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        String queryExample = "SELECT (count(?value) AS ?aggValue) WHERE {\n" +
                "  GRAPH ?graphName {\n" +
                "    ?s\n    <http://spitfire-project.eu/ontology/ns/obs>\n" +
                "        <http://localhost:8182/ld4s/resource/property/temperature> .\n" +
                "    ?s\n    <http://spitfire-project.eu/ontology/ns/value>\n" +
                "        ?value .\n  }\n}";

        ChannelBuffer htmlContentBuffer = this.getHtmlContent("1", EMPTY, queryExample, EMPTY, EMPTY);

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, htmlContentBuffer, "text/html");

        writeHttpResponse(channel, httpResponse, clientAddress);
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

        VirtualSensorFormData formData = new VirtualSensorFormData(httpRequest.getContent());

        if(!("Test".equals(formData.getSubmitButton()) || "Create".equals(formData.getSubmitButton()))
                || formData.getGraphName() == null || formData.getSparqlQuery() == null){

            handleInvalidPostRequest(httpResponseFuture, formData, httpRequest.getProtocolVersion());
        }

        else if("Test".equals(formData.getSubmitButton())){
            handlePostTestRequest(httpResponseFuture, httpRequest.getProtocolVersion(), formData);
        }

        else if("Create".equals(formData.getSubmitButton())){
            handlePostCreationRequest(httpResponseFuture, httpRequest.getProtocolVersion(), formData);
        }
    }


    private void handleInvalidPostRequest(SettableFuture<HttpResponse> httpResponseFuture,
                VirtualSensorFormData formData, HttpVersion httpVersion) throws Exception{

      ChannelBuffer contentBuffer = getHtmlContent(formData.getGraphNamePostfix(),
              formData.getGraphNameErrorHtml(), formData.getSparqlQueryString(), formData.getSparqlQueryError(), "");

      HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
              HttpResponseStatus.UNPROCESSABLE_ENTITY, contentBuffer, "text/html");

      httpResponseFuture.set(httpResponse);

    }


    private void handlePostCreationRequest(final SettableFuture<HttpResponse> httpResponseFuture,
                                           final HttpVersion httpVersion, final VirtualSensorFormData formData) {

        SlseDataOrigin dataOrigin = new SlseDataOrigin(formData.getGraphName(), formData.getSparqlQuery());

        Futures.addCallback(this.slseRegistry.registerSlseDataOrigin(dataOrigin), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                try{
                    ChannelBuffer content = getHtmlContentForCreated(formData.getGraphName());
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.CREATED, content, "text/html");

                    httpResponseFuture.set(httpResponse);
                }

                catch(Exception ex){
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

                    httpResponseFuture.set(httpResponse);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try{
                    HttpResponse httpResponse;

                    if(t instanceof GraphNameAlreadyExistsException ||
                            t instanceof IdentifierAlreadyRegisteredException){

                        ChannelBuffer content = getHtmlContent(formData.getGraphNamePostfix(),
                                t.getMessage(), formData.getSparqlQueryString(), formData.sparqlQueryError, EMPTY);

                        httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.UNPROCESSABLE_ENTITY, content, "text/html");
                    }

                    else{
                        httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.UNPROCESSABLE_ENTITY, t.getMessage());
                    }

                    httpResponseFuture.set(httpResponse);
                }

                catch(Exception ex){
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

                    httpResponseFuture.set(httpResponse);
                }
            }

        }, this.internalTasksExecutorService);
    }


    private void handlePostTestRequest(final SettableFuture<HttpResponse> httpResponseFuture,
            final HttpVersion httpVersion, final VirtualSensorFormData formData) throws Exception{

        final long startTime = System.currentTimeMillis();
        ListenableFuture<ResultSet> sparqlResultFuture = executeSparqlQuery(formData);

        Futures.addCallback(sparqlResultFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                HttpResponse httpResponse;

                try{

                    String sensorValue = createVirtualSensorXMLPayload(formData.getGraphName(), resultSet);
                    long duration = System.currentTimeMillis() - startTime;

                    ChannelBuffer contentBuffer = getHtmlContent(formData.getGraphNamePostfix(), EMPTY,
                            formData.getSparqlQueryString(), EMPTY, String.valueOf(duration), sensorValue);

                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.OK, contentBuffer, "text/html");
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
                    ChannelBuffer contentBuffer = getHtmlContent(formData.getGraphNamePostfix(), EMPTY,
                            formData.getSparqlQueryString(), EMPTY,
                            "ERROR while executing SPARQL query!!!\n\n" + t.getMessage());

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


    private SettableFuture<ResultSet> executeSparqlQuery(VirtualSensorFormData formData) throws Exception{

        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
        SparqlQueryMessage sparqlQueryMessage = new SparqlQueryMessage(formData.getSparqlQuery(), sparqlResultFuture);
        Channels.write(this.localChannel, sparqlQueryMessage);

        return sparqlResultFuture;
    }




    private String createVirtualSensorXMLPayload(URI virtualSensorUri, ResultSet resultSet){
        Model model = ModelFactory.createDefaultModel();

        //Get the first row of the result set
        RDFNode sensorValue;

        if(resultSet.hasNext()){
            QuerySolution querySolution = resultSet.nextSolution();
            sensorValue = querySolution.get(querySolution.varNames().next());
        }

        else{
            sensorValue = ResourceFactory.createTypedLiteral("0", XSDDatatype.XSDinteger);
        }

        Statement statement  = model.createStatement(
                model.createResource(virtualSensorUri.toString()),
                model.createProperty("http://spitfire-project.eu/ontology/ns/value"),
                sensorValue
            );

        model.add(statement);

        StringWriter writer = new StringWriter();
        model.write(writer, "RDF/XML");

        return writer.toString();
    }


    private ChannelBuffer getHtmlContentForCreated(URI graphName) throws Exception{
        InputStream inputStream = this.getClass().getResourceAsStream("VirtualSensorCreated.html");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder formatStringBuilder = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            formatStringBuilder.append(line);
            formatStringBuilder.append("\n");
            line = reader.readLine();
        }

        String content = String.format(
                formatStringBuilder.toString(),
                "http://" + SSP_HOST + ":" + SSP_PORT + "/?graph=" + graphName,
                "http://" + SSP_HOST + ":" + SSP_PORT + "/?graph=" + graphName
        );

        return ChannelBuffers.wrappedBuffer(content.getBytes(Charset.forName("UTF-8")));
    }



    private ChannelBuffer getHtmlContent(String graphNamePostfix, String graphNameError, String sparqlQuery,
                                         String sparqlQueryError, String virtualSensorValue)
            throws Exception{

        return getHtmlContent(graphNamePostfix, graphNameError, sparqlQuery, sparqlQueryError, EMPTY,
                virtualSensorValue);
    }


    private ChannelBuffer getHtmlContent(String graphNamePostfix, String graphNameError, String sparqlQuery,
                                         String sparqlQueryError, String duration, String virtualSensorValue)
        throws Exception{

        InputStream inputStream = this.getClass().getResourceAsStream("VirtualSensorDefinition.html");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder formatStringBuilder = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            formatStringBuilder.append(line);
            formatStringBuilder.append("\n");
            line = reader.readLine();
        }

        String content = String.format(formatStringBuilder.toString(), SLSE_GRAPH_NAME_PREFIX, graphNamePostfix,
                graphNameError, sparqlQuery, sparqlQueryError, duration, virtualSensorValue);

        return ChannelBuffers.wrappedBuffer(content.getBytes(Charset.forName("UTF-8")));
    }


    private class VirtualSensorFormData {

        private String submitButton;
        private URI graphName;
        private String graphNamePostfix;
        private String graphNameErrorHtml;
        private Query sparqlQuery;
        private String sparqlQueryString;
        private String sparqlQueryError;


        public VirtualSensorFormData(ChannelBuffer formDataBuffer) throws UnsupportedEncodingException {
            try{
                Map<String, String> formData = splitFormContent(formDataBuffer);

                this.submitButton = formData.get("button");

                try{
                    this.graphNamePostfix = formData.get("graphName");
                    this.graphName = new URI(SLSE_GRAPH_NAME_PREFIX + this.graphNamePostfix);
                    this.graphNameErrorHtml = EMPTY;
                }
                catch (URISyntaxException ex) {
                    log.error("Invalid URI for SLSE graph name!", ex);
                    this.graphName = null;
                    this.graphNameErrorHtml = getErrorHtml(1, ex.getMessage());
                }

                try{
                    //this.sparqlQueryString = formData.get("sparqlQuery");
                    String[] parts = formData.get("sparqlQuery").split("\\r?\\n|\\r");
                    StringBuilder builder = new StringBuilder();
                    for(String part : parts){
                        builder.append(part).append(System.getProperty("line.separator"));
                    }
                    this.sparqlQueryString = builder.toString();

                    log.info("SPARQL String: \n{}", this.sparqlQueryString);
                    this.sparqlQuery = QueryFactory.create(this.sparqlQueryString);
                    this.sparqlQueryError = EMPTY;
                }
                catch(QueryException ex){
                    log.error("Malformed SPARQL query for virtual sensor!", ex);
                    this.sparqlQuery = null;
                    this.sparqlQueryError = getErrorHtml(10, ex.getMessage());
                }
            }

            catch (Exception ex){
                log.error("This should never happen!", ex);
                this.graphName = null;
                this.graphNameErrorHtml = ex.getMessage();
                this.sparqlQuery = null;
                this.sparqlQueryError = ex.getMessage();
            }

        }

        public URI getGraphName() {
            return graphName;
        }

        public Query getSparqlQuery() {
            return sparqlQuery;
        }

        public String getSparqlQueryString(){
            return this.sparqlQueryString;
        }

        public String getGraphNameErrorHtml() {
            return graphNameErrorHtml;
        }

        public String getSparqlQueryError() {
            return sparqlQueryError;
        }

        public String getSubmitButton() {
            return submitButton;
        }

        public String getGraphNamePostfix(){
            return this.graphNamePostfix;
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

        private String getErrorHtml(int rows, String errorMessage){
            return "<p><textarea class=errorfield rows=\"" + rows + "\" cols=\"100\"" +
                    "readonly>" + errorMessage + "</textarea></p>";
        }
    }
}
