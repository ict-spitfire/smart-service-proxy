package eu.spitfire.ssp.backends.internal.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensor;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorRegistry;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import eu.spitfire.ssp.utils.Language;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.MixedAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 05.05.14.
 */
public class VirtualSensorCreator extends AbstractVirtualSensorCreator {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private LocalServerChannel localChannel;
    private VirtualSensorRegistry registry;

    public VirtualSensorCreator(VirtualSensorBackendComponentFactory componentFactory){
        super(
                componentFactory,
                "http://" + componentFactory.getSspHostName() + "/virtual-sensor",
                "html/semantic-entities/virtual-sensor-creation.html"
        );

        this.localChannel = componentFactory.getLocalChannel();
        this.registry = componentFactory.getRegistry();
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
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.getIoExecutor());


        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);

        Query sparqlQuery = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("query")).getValue());
        Model model = createModel(((MixedAttribute) decoder.getBodyHttpData("ontology")).getValue());
        String sensorName = ((MixedAttribute) decoder.getBodyHttpData("sensorName")).getValue();
        String buttonID = ((MixedAttribute) decoder.getBodyHttpData("button")).getValue();

        if("btnTest".equals(buttonID)){
            handlePostTestRequest(httpResponseFuture, sensorName, model, sparqlQuery, httpRequest.getProtocolVersion());
        }

        else if("btnCreate".equals(buttonID)){
            handlePostCreationRequest(httpResponseFuture, sensorName, model, sparqlQuery, httpRequest.getProtocolVersion());
        }
    }


    private void handlePostCreationRequest(final SettableFuture<HttpResponse> httpResponseFuture, final String sensorName,
                                           final Model model, final Query sparqlQuery, final HttpVersion httpVersion)
            throws Exception{

        final URI graphName = new URI("http://example.org/virtual-sensors#" + sensorName);

        //Add sensor value to model
        ListenableFuture<Model> initialStatusFuture = addSensorValueToModel(sensorName, model, sparqlQuery);
        Futures.addCallback(initialStatusFuture, new FutureCallback<Model>() {
            @Override
            public void onSuccess(Model model) {
                //Register virtual sensor
                final VirtualSensor virtualSensor = new VirtualSensor(graphName, sparqlQuery, localChannel, getInternalTasksExecutor());
                final ExpiringNamedGraph initialStatus = new ExpiringNamedGraph(graphName, model);

                ListenableFuture<Void> registrationFuture = registry.registerDataOrigin(virtualSensor, initialStatus);
                Futures.addCallback(registrationFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Map<String, String> content = new HashMap<>();

                        content.put("graphName", virtualSensor.getGraphName().toASCIIString());
                        content.put("regResult", "OK");

                        httpResponseFuture.set(HttpResponseFactory.createHttpJsonResponse(httpVersion, content));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Map<String, String> result = new HashMap<>();

                        result.put("graphName", virtualSensor.getGraphName().toASCIIString());
                        result.put("regResult", "OK");

                        httpResponseFuture.set(HttpResponseFactory.createHttpJsonResponse(httpVersion, result));
                    }
                });

//                registerVirtualSensor(virtualSensor, model, httpResponseFuture, httpVersion);
            }

            @Override
            public void onFailure(Throwable t) {
                httpResponseFuture.setException(t);
            }
        });
    }


//    private void registerVirtualSensor(final VirtualSensor virtualSensor, final Model initialStatus,
//                                       final SettableFuture<HttpResponse> httpResponseFuture,
//                                       final HttpVersion httpVersion){
//
//
//        Futures.addCallback(this.registry.registerDataOrigin(virtualSensor), new FutureCallback<Void>() {
//            @Override
//            public void onSuccess(Void pVoid) {
//                try{
//                    //Cache initial status
//                    ExpiringNamedGraph statusMessage = new ExpiringNamedGraph(
//                            virtualSensor.getGraphName(), initialStatus
//                    );
//
//                    ChannelFuture future = Channels.write(localChannel, statusMessage);
//
//                    Map<String, String> result = new HashMap<>();
//
//                    result.put("graphName", virtualSensor.getGraphName().toASCIIString());
//                    result.put("regResult", "OK");
//
//                    httpResponseFuture.set(
//                            HttpResponseFactory.createHttpJsonResponse(httpVersion, result)
//                    );
//                }
//
//                catch(Exception ex){
//                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
//                            HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
//
//                    httpResponseFuture.set(httpResponse);
//                }
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
//                        httpVersion, HttpResponseStatus.UNPROCESSABLE_ENTITY, t.getMessage()
//                );
//
//                httpResponseFuture.set(httpResponse);
//            }
//        });
//    }


    private void handlePostTestRequest(final SettableFuture<HttpResponse> httpResponseFuture, final String sensorName,
            final Model model, final Query sparqlQuery, final HttpVersion httpVersion) throws Exception{

        final long startTime = System.currentTimeMillis();

        Futures.addCallback(addSensorValueToModel(sensorName, model, sparqlQuery), new FutureCallback<Model>() {
            @Override
            public void onSuccess(Model model) {
                HttpResponse httpResponse;

                try{
                    StringWriter n3Writer = new StringWriter();
                    model.write(n3Writer, Language.RDF_N3.lang);

                    Map<String, String> result = new HashMap<>();

                    result.put("sensorStatus", n3Writer.toString());
                    result.put("duration", String.valueOf(System.currentTimeMillis() - startTime));

                    httpResponse = HttpResponseFactory.createHttpJsonResponse(httpVersion, result);
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
                log.warn("Exception while executing SPARQL query: {}", sparqlQuery, t);

                HttpResponse httpResponse;

                try{
                    httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.UNPROCESSABLE_ENTITY, t.getMessage());
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



}
