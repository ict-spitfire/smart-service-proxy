package eu.spitfire.ssp.backend.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import de.uzl.itm.ssp.jaxb4vs.jaxb.JAXBVirtualSensor;
import de.uzl.itm.ssp.jaxb4vs.tools.VirtualSensorsUnmarshaller;
import eu.spitfire.ssp.backend.vs.VirtualSensor;
import eu.spitfire.ssp.backend.vs.VirtualSensorsComponentFactory;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
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
import java.util.*;

/**
 * Created by olli on 25.06.14.
 */
public class VirtualSensorBatchCreator extends AbstractVirtualSensorCreator {

    private static Logger LOG = LoggerFactory.getLogger(VirtualSensorBatchCreator.class.getName());

    public VirtualSensorBatchCreator(VirtualSensorsComponentFactory componentFactory){
        super(
                componentFactory,
                "html/services/virtual-sensor-batch-creation.html"
        );
    }


    protected void processPost(final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress)
            throws Exception {

        InputStream xmlInputStream = getXMLInputStream(httpRequest);
        final List<JAXBVirtualSensor> virtualSensors =
                VirtualSensorsUnmarshaller.unmarshal(xmlInputStream).getVirtualSensors();

        final LinkedHashMap<URI, ListenableFuture<Void>> registrationFutures = new LinkedHashMap<>(virtualSensors.size());

        //Register all semantic entities one by one
        for(JAXBVirtualSensor vs : virtualSensors){

            URI sensorName = addPrefix(vs.getSensorName());
            URI sensorType = new URI(vs.getSensorType());
            URI featureOfInterest = new URI(vs.getFeatureOfInterest());
            URI observedProperty = new URI(vs.getObservedProperty());
            Query query = QueryFactory.create(vs.getSparqlQuery());


            VirtualSensor virtualSensor = createVirtualSensor(
                sensorName, sensorType, featureOfInterest, observedProperty, query
            );
            registrationFutures.put(sensorName, registerVirtualSensor(virtualSensor));
        }

        //Combine all futures to a single one
        ListenableFuture<List<Void>> futures = Futures.successfulAsList(new HashSet<>(registrationFutures.values()));

        Futures.addCallback(futures, new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(List<Void> voids) {
                sendRegistrationResult(registrationFutures, channel, httpRequest, clientAddress);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("This should never happen!", throwable);
            }
        });
    }


    private void sendRegistrationResult(final Map<URI, ListenableFuture<Void>> registrationFutures,
                    final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress){

//        final Map<String, String> registrationResult = new HashMap<>();
//        final SettableFuture<Map<String, String>> registrationResultFuture = SettableFuture.create();

        Map<String, String> graphName2ResultsMap = new HashMap<>();
        for(Map.Entry<URI, ListenableFuture<Void>> entry : registrationFutures.entrySet()) {
            String graphName = entry.getKey().toString();
            ListenableFuture<Void> future = entry.getValue();
            try{
                // future.get() will throw an exception if registration was not successful
                future.get();
                graphName2ResultsMap.put(graphName, "OK");
            }
            catch(Exception ex) {
                graphName2ResultsMap.put(graphName, "Error: " + ex.getMessage());
            }
        }

        HttpResponse httpResponse = HttpResponseFactory.createHttpJsonResponse(
            httpRequest.getProtocolVersion(), graphName2ResultsMap
        );

        writeHttpResponse(channel, httpResponse, clientAddress);

//            Futures.addCallback(registrationFutures.get(graphName), new FutureCallback<Void>() {
//                @Override
//                public void onSuccess(Void result) {
//                    LOG.info("Successfully registered virtual sensor with graph name : {}", graphName);
//                    registrationResult.put(graphName.toASCIIString(), "OK");
//
//                    if(registrationResult.size() == registrationFutures.size()){
//                        registrationResultFuture.set(registrationResult);
//                    }
//                }
//
//                @Override
//                public void onFailure(Throwable t) {
//                    LOG.error("Could not register virtual sensor with graph name: {}", graphName, t);
//                    registrationResult.put(graphName.toASCIIString(), t.getMessage());
//
//                    if(registrationResult.size() == registrationFutures.size()){
//                        registrationResultFuture.set(registrationResult);
//                    }
//                }
//
//            });
        }


//        //Await the registrations to be done and send the response...
//        Futures.addCallback(registrationResultFuture, new FutureCallback<Map<String, String>>() {
//
//            @Override
//            public void onSuccess(Map<String, String> result) {
//                sendResult();
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                sendResult();
//            }
//
//            private void sendResult(){
//                HttpResponse httpResponse = HttpResponseFactory.createHttpJsonResponse(
//                        httpRequest.getProtocolVersion(), registrationResult
//                );
//
//                writeHttpResponse(channel, httpResponse, clientAddress);
//            }
//
//        }, this.getIoExecutor());
//    }


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
