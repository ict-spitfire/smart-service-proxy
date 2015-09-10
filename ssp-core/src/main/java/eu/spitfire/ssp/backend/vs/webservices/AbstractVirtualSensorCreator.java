package eu.spitfire.ssp.backend.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.vs.VirtualSensor;
import eu.spitfire.ssp.backend.vs.VirtualSensorsComponentFactory;
import eu.spitfire.ssp.backend.vs.VirtualSensorsRegistry;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import org.apache.jena.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Created by olli on 30.06.14.
 */
public abstract class AbstractVirtualSensorCreator extends HttpWebservice{

    private static Logger LOG = LoggerFactory.getLogger(AbstractVirtualSensorCreator.class.getName());

    private VirtualSensorsComponentFactory componentFactory;


    protected URI addPrefix(String sensorName) throws URISyntaxException {
        return new URI(String.format(Locale.ENGLISH, VS_GRAPHNAME_TEMPLATE, sensorName));
    }


    protected final String VS_PREFIX;
    protected final String VS_GRAPHNAME_TEMPLATE;

    protected AbstractVirtualSensorCreator(VirtualSensorsComponentFactory componentFactory, String htmlResourcePath){

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);
        this.componentFactory = componentFactory;

        String port = componentFactory.getPort() == 80 ? "" : ":" + componentFactory.getPort();
        VS_PREFIX = "http://" + componentFactory.getHostName() + port + "/vs#";
        VS_GRAPHNAME_TEMPLATE = VS_PREFIX + "%s";
    }


    protected VirtualSensor createVirtualSensor(URI sensorName, URI sensorType, URI foi, URI property, Query query)
            throws Exception {

        return new VirtualSensor(sensorName, sensorType, foi, property, query, componentFactory.getLocalChannel(),
                componentFactory.getInternalTasksExecutor());
    }


    protected ListenableFuture<Void> registerVirtualSensor(VirtualSensor virtualSensor){

        SettableFuture<Void> registrationFuture = SettableFuture.create();

        // await the result of the first observation
        Futures.addCallback(virtualSensor.makeSingleObservation(), new FutureCallback<Long>() {
            @Override
            public void onSuccess(Long aLong) {
                VirtualSensorsRegistry registry = componentFactory.getRegistry();
                ExpiringNamedGraph graph = new ExpiringNamedGraph(
                        virtualSensor.getGraphName(), virtualSensor.createGraphAsModel()
                );

                //await the registration
                Futures.addCallback(registry.registerDataOrigin(virtualSensor, graph), new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        registrationFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        registrationFuture.setException(throwable);
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                registrationFuture.setException(throwable);
            }
        });

        return registrationFuture;
    }


//    // creates a TTL-representation of the virtual sensors graph
//    private String createVirtualSensorGraph(String sensorName, URI sensorType, URI foi, URI property){
//        return String.format(Locale.ENGLISH, VS_INSTANCE_TEMPLATE, VS_PREFIX, sensorName, sensorType.toString(),
//            property.toString(), foi.toString(), property.toString(), sensorName, foi.toString(), property.toString(),
//            property.toString(), property.toString());
//    }
//
//
//    protected Model createModel(String sensorName, URI sensorType, URI foi, URI property){
//        Model result = ModelFactory.createDefaultModel();
//        String ttl = createVirtualSensorGraph(sensorName, sensorType, foi, property);
//        InputStream stream = new ByteArrayInputStream(ttl.getBytes(Charset.forName("UTF8")));
//
//        result.read(stream, null, Language.RDF_TURTLE.lang);
//        return result;
//    }




//        virtualSensor.createGraphAsModel();
//        Model model = createModel(sensorName, sensorType, foi, property);
//        URI graphName = createGraphName(sensorName);

//        ListenableFuture<Model> initialStatusFuture = addSensorValueToModel(graphName, model, query);
//        SettableFuture<Void> sensorCreationFuture = SettableFuture.create();

//        Futures.addCallback(initialStatusFuture, new FutureCallback<Model>() {
//            @Override
//            public void onSuccess(Model model) {
//
//                //Register virtual sensor
//                final VirtualSensor virtualSensor = new VirtualSensor(
//                        sensorName, sensorType, foi, property, query, "?val", localChannel, getInternalExecutor()
//                );
//                final ExpiringNamedGraph initialStatus = new ExpiringNamedGraph(graphName, model);
//
//                VirtualSensorRegistry registry = getVirtualSensorRegistry();
//                ListenableFuture<Void> registrationFuture = registry.registerDataOrigin(virtualSensor, initialStatus);
//
//                Futures.addCallback(registrationFuture, new FutureCallback<Void>() {
//                    @Override
//                    public void onSuccess(Void aVoid) {
//                        sensorCreationFuture.set(null);
//                    }
//
//                    @Override
//                    public void onFailure(Throwable throwable) {
//                        sensorCreationFuture.setException(throwable);
//                    }
//                });
//            }
//
//            @Override
//            public void onFailure(Throwable throwable) {
//                sensorCreationFuture.setException(throwable);
//            }
//        });

//        return sensorCreationFuture;
//    }


//    protected ListenableFuture<Model> addSensorValueToModel(final URI sensorName, final Model model, final Query query){
//        final SettableFuture<Model> modelFuture = SettableFuture.create();
//
//        Futures.addCallback(executeSparqlQuery(query), new FutureCallback<ResultSet>() {
//            @Override
//            public void onSuccess(ResultSet resultSet) {
//                try {
//
//                    RDFNode sensorValue;
//
//                    if (resultSet.hasNext()) {
//                        QuerySolution querySolution = resultSet.nextSolution();
//                        if(querySolution.contains("?aggVal")){
//                            sensorValue = ResourceFactory.createPlainLiteral(querySolution.get("?aggVal").toString());
//                        }
//                        else{
//                            sensorValue = model.createTypedLiteral(0);
//                        }
//                    }
//                    else{
//                        sensorValue = ResourceFactory.createTypedLiteral(0);
//                    }
//
//                    Resource subject = model.getResource(sensorName + "-Result");
//
//                    Statement statement = subject.getProperty(
//                            model.getProperty("http://purl.oclc.org/NET/ssnx/ssn#", "hasValue")
//                    );
//
//                    statement.changeObject(sensorValue);
//
//                    modelFuture.set(model);
//
//                } catch (Exception ex) {
//                    modelFuture.setException(ex);
//                }
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                LOG.warn("Exception while executing SPARQL query: {}", query, t);
//                modelFuture.setException(t);
//            }
//        });
//
//        return modelFuture;
//    }

//    private SettableFuture<ResultSet> executeSparqlQuery(Query sparqlQuery){
//
//        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
//        InternalQueryExecutionRequest internalQueryExecutionRequest = new InternalQueryExecutionRequest(sparqlQuery, sparqlResultFuture);
//        Channels.write(this.localChannel, internalQueryExecutionRequest);
//
//        return sparqlResultFuture;
//    }

//    protected VirtualSensorsRegistry getVirtualSensorsRegistry(){
//        return this.virtualSensorsRegistry;
//    }
}
