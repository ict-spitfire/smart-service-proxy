package eu.spitfire.ssp.backends.internal.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.xsd.vs.tools.VirtualSensorsUnmarshaller;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensor;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jboss.netty.handler.codec.http.multipart.MixedFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by olli on 25.06.14.
 */
public class VirtualSensorBatchCreator extends AbstractVirtualSensorCreator {

    private Logger log = LoggerFactory.getLogger(VirtualSensorBatchCreator.class.getName());

    public VirtualSensorBatchCreator(VirtualSensorBackendComponentFactory componentFactory){
        super(
                componentFactory,
                "http://" + componentFactory.getSspHostName() + "/virtual-sensor",
                "html/semantic-entities/virtual-sensor-batch-creation.html"
        );
    }


    protected void processPost(final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress)
            throws Exception {

        InputStream xmlInputStream = getXMLInputStream(httpRequest);
        final List<de.uniluebeck.itm.xsd.vs.jaxb.VirtualSensor> virtualSensors =
                VirtualSensorsUnmarshaller.unmarshal(xmlInputStream).getVirtualSensors();

        final Map<URI, ListenableFuture<Void>> registrationFutures = new LinkedHashMap<>(virtualSensors.size());

        //Register all semantic entities one by one
        for(de.uniluebeck.itm.xsd.vs.jaxb.VirtualSensor jaxbVirtualSensor : virtualSensors){

            String sensorName = jaxbVirtualSensor.getSensorName();
            final URI graphName =  new URI("http://example.org/virtual-sensors#" + sensorName);
            Query query = QueryFactory.create(jaxbVirtualSensor.getQuery());

            final VirtualSensor virtualSensor = new VirtualSensor(graphName, query);

            ListenableFuture<Model> initialStatusFuture = addSensorValueToModel(
                    sensorName, createModel(jaxbVirtualSensor.getOntology()), query
            );

            Futures.addCallback(initialStatusFuture, new FutureCallback<Model>() {
                @Override
                public void onSuccess(Model initialStatus) {
                    ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, initialStatus);
                    registrationFutures.put(
                            virtualSensor.getGraphName(),
                            getVirtualSensorRegistry().registerDataOrigin(virtualSensor, expiringNamedGraph)
                    );

                    if(registrationFutures.size() == virtualSensors.size()){
                        sendRegistrationResult(registrationFutures, channel, httpRequest, clientAddress);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    SettableFuture<Void> registrationFuture = SettableFuture.create();
                    registrationFuture.setException(t);
                    registrationFutures.put(graphName, registrationFuture);

                    if(registrationFutures.size() == virtualSensors.size()){
                        sendRegistrationResult(registrationFutures, channel, httpRequest, clientAddress);
                    }
                }
            });
        }
    }

    private void sendRegistrationResult(final Map<URI, ListenableFuture<Void>> registrationFutures,
                    final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress){

        final Map<String, String> registrationResult = new HashMap<>();
        final SettableFuture<Map<String, String>> registrationResultFuture = SettableFuture.create();

        //Await the registration results
        for(final URI graphName : registrationFutures.keySet()){

            Futures.addCallback(registrationFutures.get(graphName), new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    log.info("Successfully registered virtual sensor with graph name : {}", graphName);
                    registrationResult.put(graphName.toASCIIString(), "OK");

                    if(registrationResult.size() == registrationFutures.size()){
                        registrationResultFuture.set(registrationResult);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Could not register virtual sensor with graph name: {}", graphName, t);
                    registrationResult.put(graphName.toASCIIString(), t.getMessage());

                    if(registrationResult.size() == registrationFutures.size()){
                        registrationResultFuture.set(registrationResult);
                    }
                }

            });
        }

        //Await the registrations to be done and send the response...
        Futures.addCallback(registrationResultFuture, new FutureCallback<Map<String, String>>() {

            @Override
            public void onSuccess(Map<String, String> result) {
                sendResult();
            }

            @Override
            public void onFailure(Throwable t) {
                sendResult();
            }

            private void sendResult(){
                HttpResponse httpResponse = HttpResponseFactory.createHttpJsonResponse(
                        httpRequest.getProtocolVersion(), registrationResult
                );

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.getIoExecutor());
    }


    private InputStream getXMLInputStream(HttpRequest httpRequest) throws Exception{
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);

        while(decoder.hasNext()){
            InterfaceHttpData httpData = decoder.next();
            if(httpData.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload){
                if(httpData instanceof MixedFileUpload){
                    MixedFileUpload mixedFileUpload = (MixedFileUpload) httpData;
                    return new ByteArrayInputStream(mixedFileUpload.get());
                }
            }
        }

        return null;
    }






}
