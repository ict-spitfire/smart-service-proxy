package eu.spitfire.ssp.backends.internal.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensor;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorRegistry;
import eu.spitfire.ssp.server.internal.messages.requests.QueryProcessingRequest;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.Language;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Created by olli on 30.06.14.
 */
public abstract class AbstractVirtualSensorCreator extends HttpWebservice{



    private static Logger LOG = LoggerFactory.getLogger(AbstractVirtualSensorCreator.class.getName());

    private VirtualSensorRegistry virtualSensorRegistry;
    private LocalServerChannel localChannel;
    //private String graphNamePrefix;

    private static final String VS_ONTOLOGY = "";
//            "//>\"vs:VirtualSensor a rdfs:Class ;\\n>\" +\n" +
//                    "//>\"  rdfs:subClassOf ssn:Sensor .\\n\\n>\" +\n" +
//                    "//\n" +
//                    "//>\"vs:VirtualSensorOutput a rdfs:Class ;\\n>\" +\n" +
//                    "//>\"  rdfs:subClassOf ssn:SensorOutput .\\n\\n>\" +\n" +
//                    "//\n" +
//                    "//>\"vs:virtualProperty a rdf:Property ;\\n>\" +\n" +
//                    "//>\"  rdfs:subPropertyOf ssn:hasProperty .\\n\\n>\" +\n" +
//                    "//\n" +
//                    "//>\"vs:VirtualProperty a rdfs:Class ;\\n>\" +\n" +
//                    "//>\"  rdfs:subClassOf ssn:Property .\\n\\n>\" +\n" +
//                    "//\n" +
//                    "//>\"vs:>\" + $(>\"#fldTypeName>\").val() + >\" a rdfs:Class ;\\n>\" +\n" +
//                    "//>\"  rdfs:subClassOf vs:VirtualSensor .\\n\\n>\" +";

    private static final String VS_GRAPHNAME_TEMPLATE = "http://example.org/virtual-sensors#%s";

    protected static URI createGraphName(String sensorName) throws URISyntaxException {
        return new URI(String.format(Locale.ENGLISH, VS_GRAPHNAME_TEMPLATE, sensorName));
    }


    private static final String VS_INSTANCE_TEMPLATE =
            "@prefix osm: <http://example.org/osm#> .\n" +
            "@prefix vs: <http://example.org/virtual-sensors#> .\n" +
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> . \n" +
            "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> . \n\n" +

            "vs:%s a vs:%s ;\n\t" +
                "ssn:observes _:property .\n\n" +

            "#########################################################\n" +
            "# The \"Observation\" is the central piece of information\n" +
            "#########################################################\n\n" +

            "vs:%s-Observation a ssn:Observation ;\n\t" +
                "ssn:featureOfInterest <%s> ;\n\t" +
                "ssn:observedProperty _:property ;\n\t" +
                "ssn:observedBy vs:%s ;\n\t" +
                "ssn:observedResult vs:%s-Result .\n\n" +

            "#########################################################\n" +
            "# The \"SensorOutput\" links to the sensed value\n" +
            "#########################################################\n\n" +

            "vs:%s-Result a ssn:SensorOutput ;\n\t" +
                "ssn:hasValue \"0\"^^xsd:double .\n\n" +

            "#########################################################\n" +
            "# Add the \"estimated\" property to the FOI\n" +
            "#########################################################\n\n" +

            "<%s> ssn:hasProperty _:property ;\n\t" +
                "a ssn:FeatureOfInterest .";

//            "_:property ssn:isPropertyOf <%s> .";


    private static String createVirtualSensorInstance(String sensorName, String sensorType, URI foi){
        return String.format(Locale.ENGLISH, VS_INSTANCE_TEMPLATE, sensorName, sensorType, sensorName, foi.toString(),
                sensorName, sensorName, sensorName, foi.toString(), foi.toString());
    }


    protected AbstractVirtualSensorCreator(VirtualSensorBackendComponentFactory componentFactory,
                                           String htmlResourcePath){

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);
        this.virtualSensorRegistry = componentFactory.getRegistry();
        this.localChannel = componentFactory.getLocalChannel();
    }


    protected Model createModel(String sensorName, String sensorType, URI foi){
        Model result = ModelFactory.createDefaultModel();
        String ttl = createVirtualSensorInstance(sensorName, sensorType, foi);
        InputStream stream = new ByteArrayInputStream(ttl.getBytes(Charset.forName("UTF8")));

        result.read(stream, null, Language.RDF_TURTLE.lang);
        return result;
    }

    protected ListenableFuture<Void> createVirtualSensor(String sensorName, String sensorType, URI foi,
            final Query query) throws URISyntaxException {

        Model model = createModel(sensorName, sensorType, foi);
        URI graphName = createGraphName(sensorName);

        ListenableFuture<Model> initialStatusFuture = addSensorValueToModel(graphName, model, query);
        SettableFuture<Void> sensorCreationFuture = SettableFuture.create();

        Futures.addCallback(initialStatusFuture, new FutureCallback<Model>() {
            @Override
            public void onSuccess(Model model) {

                //Register virtual sensor
                final VirtualSensor virtualSensor = new VirtualSensor(
                        graphName, query, localChannel, getInternalTasksExecutor()
                );
                final ExpiringNamedGraph initialStatus = new ExpiringNamedGraph(graphName, model);

                VirtualSensorRegistry registry = getVirtualSensorRegistry();
                ListenableFuture<Void> registrationFuture = registry.registerDataOrigin(virtualSensor, initialStatus);

                Futures.addCallback(registrationFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        sensorCreationFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        sensorCreationFuture.setException(throwable);
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                sensorCreationFuture.setException(throwable);
            }
        });

        return sensorCreationFuture;
    }


    protected ListenableFuture<Model> addSensorValueToModel(final URI sensorName, final Model model, final Query query){
        final SettableFuture<Model> modelFuture = SettableFuture.create();

        Futures.addCallback(executeSparqlQuery(query), new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                try {

                    RDFNode sensorValue;

                    if (resultSet.hasNext()) {
                        QuerySolution querySolution = resultSet.nextSolution();
                        if(querySolution.contains("?aggVal")){
                            sensorValue = ResourceFactory.createPlainLiteral(querySolution.get("?aggVal").toString());
                        }
                        else{
                            sensorValue = model.createTypedLiteral(0);
                        }
                    }
                    else{
                        sensorValue = ResourceFactory.createTypedLiteral(0);
                    }

                    Resource subject = model.getResource(sensorName + "-Result");

                    Statement statement = subject.getProperty(
                            model.getProperty("http://purl.oclc.org/NET/ssnx/ssn#", "hasValue")
                    );

                    statement.changeObject(sensorValue);

                    modelFuture.set(model);

                } catch (Exception ex) {
                    modelFuture.setException(ex);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.warn("Exception while executing SPARQL query: {}", query, t);
                modelFuture.setException(t);
            }
        });

        return modelFuture;
    }

    private SettableFuture<ResultSet> executeSparqlQuery(Query sparqlQuery){

        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
        QueryProcessingRequest queryProcessingRequest = new QueryProcessingRequest(sparqlQuery, sparqlResultFuture);
        Channels.write(this.localChannel, queryProcessingRequest);

        return sparqlResultFuture;
    }

    protected VirtualSensorRegistry getVirtualSensorRegistry(){
        return this.virtualSensorRegistry;
    }
}
