package eu.spitfire.ssp.backend.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.vs.VirtualSensor;
import eu.spitfire.ssp.backend.vs.VirtualSensorsComponentFactory;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.MixedAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 05.05.14.
 */
public class VirtualSensorCreator extends AbstractVirtualSensorCreator {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    public VirtualSensorCreator(VirtualSensorsComponentFactory componentFactory){
        super(componentFactory, "html/services/virtual-sensor-creation.html");
    }


    @Override
    protected void processPost(final Channel channel, final HttpRequest httpRequest,
                             final InetSocketAddress clientAddress) throws Exception{

        SettableFuture<HttpResponse> httpResponseFuture = SettableFuture.create();
        Futures.addCallback(httpResponseFuture, new FutureCallback<HttpResponse>() {

            @Override
            public void onSuccess(HttpResponse httpResponse) {
                writeHttpResponse(channel, httpResponse, clientAddress);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                    httpRequest.getProtocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage()
                );

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.getIoExecutor());

        HttpVersion httpVersion = httpRequest.getProtocolVersion();
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);

        Query query = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("query")).getValue());
        String sensorName = ((MixedAttribute) decoder.getBodyHttpData("sensorName")).getValue();
        URI sensorType = new URI(((MixedAttribute) decoder.getBodyHttpData("sensorType")).getValue());
        URI foi = new URI(((MixedAttribute) decoder.getBodyHttpData("foi")).getValue());
        URI property = new URI(((MixedAttribute) decoder.getBodyHttpData("property")).getValue());
        String buttonID = ((MixedAttribute) decoder.getBodyHttpData("button")).getValue();


        if("btnPreview".equals(buttonID)){
            handlePreviewRequest(
                    httpResponseFuture, addPrefix(sensorName), sensorType, foi, property, query, httpVersion
            );
        }

        else if("btnCreate".equals(buttonID)){
            handleCreationRequest(
                    httpResponseFuture, addPrefix(sensorName), sensorType, foi, property, query, httpVersion
            );
        }
    }


    private void handleCreationRequest(final SettableFuture<HttpResponse> responseFuture, final URI sensorName,
        final URI sensorType, final URI foi, final URI property, final Query query, final HttpVersion httpVersion)
            throws Exception{

        VirtualSensor virtualSensor = createVirtualSensor(sensorName, sensorType, foi, property, query);

        Futures.addCallback(registerVirtualSensor(virtualSensor), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Map<String, String> responseContent = new HashMap<>();
                responseContent.put("graphName", virtualSensor.getGraphName().toASCIIString());
                responseContent.put("regResult", "OK");

                HttpResponse response = HttpResponseFactory.createHttpJsonResponse(httpVersion, responseContent);
                responseFuture.set(response);
            }

            @Override
            public void onFailure(Throwable throwable) {
                HttpResponse response = HttpResponseFactory.createHttpResponse(httpVersion,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage()
                );
                responseFuture.set(response);
            }
        });
    }



    private void handlePreviewRequest(final SettableFuture<HttpResponse> responseFuture, final URI sensorName,
        final URI sensorType, final URI foi, final URI property, final Query query, final HttpVersion httpVersion)
                throws Exception{

        // create the virtual sensor instance
        final VirtualSensor sensor = createVirtualSensor(sensorName , sensorType, foi, property, query);

        // await update of sensor value
        Futures.addCallback(sensor.makeSingleObservation(), new FutureCallback<Long>() {
            @Override
            public void onSuccess(Long duration) {

                // create model and serialize to turtle
                Model model = sensor.createGraphAsModel();
                OutputStream outputStream = new ByteArrayOutputStream();
                RDFDataMgr.write(outputStream, model, RDFFormat.TURTLE_BLOCKS);

                // create the HTTP response
                Map<String, String> content = new HashMap<>();
                content.put("sensorStatus", outputStream.toString());
                content.put("duration", duration.toString());

                responseFuture.set(HttpResponseFactory.createHttpJsonResponse(httpVersion, content));
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("This should never happen.", throwable);
                HttpResponse response = HttpResponseFactory.createHttpResponse(
                    httpVersion, HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage()
                );
                responseFuture.set(response);
            }
        });



//        Model model = createModel(sensorName, sensorType, foi, property);
//        final URI graphName = createGraphName(sensorName);
//
//        final long startTime = System.currentTimeMillis();
//
//        ListenableFuture<Model> modelFuture = addSensorValueToModel(graphName, model, query);
//
//        Futures.addCallback(modelFuture, new FutureCallback<Model>() {
//            @Override
//            public void onSuccess(Model model) {
//                HttpResponse httpResponse;
//
//                try{
//                    StringWriter writer = new StringWriter();
//                    model.write(writer, Language.RDF_TURTLE.lang);
//
//                    Map<String, String> result = new HashMap<>();
//
//                    result.put("sensorStatus", writer.toString());
//                    result.put("duration", String.valueOf(System.currentTimeMillis() - startTime));
//
//                    httpResponse = HttpResponseFactory.createHttpJsonResponse(httpVersion, result);
//                }
//
//                catch (Exception e) {
//                    log.error("This should never happen.", e);
//                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
//                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
//                }
//
//                httpResponseFuture.set(httpResponse);
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                log.error("Exception while executing SPARQL query: {}", query, t);
//
//                HttpResponse httpResponse;
//
//                try{
//                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
//                            HttpResponseStatus.UNPROCESSABLE_ENTITY, t.getMessage());
//                }
//
//                catch (Exception e) {
//                    log.error("This should never happen.", e);
//                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
//                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage() == null ? e.toString() : e.getMessage());
//                }
//
//                httpResponseFuture.set(httpResponse);
//            }
//        });

    }



}
