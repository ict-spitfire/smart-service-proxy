package eu.spitfire.ssp.backends.internal.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.hp.hpl.jena.query.QueryFactory;
import de.uniluebeck.itm.xsd.slse.jaxb.SemanticEntity;
import de.uniluebeck.itm.xsd.slse.tools.SemanticEntityUnmarshaller;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensor;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jboss.netty.handler.codec.http.multipart.MixedFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
        List<SemanticEntity> semanticEntities = SemanticEntityUnmarshaller.unmarshal(xmlInputStream).getEntities();

        Map<URI, ListenableFuture<Void>> registrationFutures = new LinkedHashMap<>(semanticEntities.size());
        final Map<String, String> registrationResult = new HashMap<>();

        //Register all semantic entities one by one
        for(SemanticEntity semanticEntity : semanticEntities){

            VirtualSensor virtualSensor = new VirtualSensor(
                    this.createGraphName(semanticEntity.getUriPath().getValue()),
                    QueryFactory.create(semanticEntity.getSparqlQuery().getValue())
            );

            registrationFutures.put(
                    virtualSensor.getGraphName(),
                    this.getVirtualSensorRegistry().registerDataOrigin(virtualSensor)
            );
        }

        //Await the registration results
        for(final URI graphName : registrationFutures.keySet()){

            Futures.addCallback(registrationFutures.get(graphName), new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    log.info("Successfully registered virtual sensor with graph name : {}", graphName);
                    registrationResult.put(graphName.toASCIIString(), "OK");
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Could not register virtual sensor with graph name: {}", graphName, t);
                    registrationResult.put(graphName.toASCIIString(), t.getMessage());
                }

            });
        }

        //Await the combined registration future, i.e. all registrations are done
        Futures.addCallback(Futures.allAsList(registrationFutures.values()), new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(@Nullable List<Void> result) {
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
