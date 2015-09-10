package eu.spitfire.ssp.backend.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.generic.DataOrigin;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import eu.spitfire.ssp.server.internal.message.InternalQueryExecutionRequest;
import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.SplitIRI;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensor extends DataOrigin<URI>{

    private static Logger LOG = LoggerFactory.getLogger(VirtualSensor.class.getName());

    /**
     * <a href="http://www.w3.org/2001/XMLSchema#">http://www.w3.org/2001/XMLSchema#</a>
     */
    public static final String XSD_NAMESAPCE = "http://www.w3.org/2001/XMLSchema#";

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#">http://purl.oclc.org/NET/ssnx/ssn#</a>
     */
    public static final String SSN_NAMESPACE = "http://purl.oclc.org/NET/ssnx/ssn#";

    /**
     * <a href="http://www.w3.org/1999/02/22-rdf-syntax-ns#">http://www.w3.org/1999/02/22-rdf-syntax-ns#</a>
     */
    public static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /**
     * <a href="http://www.w3.org/1999/02/22-rdf-syntax-ns#type">http://www.w3.org/1999/02/22-rdf-syntax-ns#type</a>
     */
    public static final Property RDF_TYPE = ResourceFactory.createProperty(RDF_NAMESPACE, "type");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#observes">http://purl.oclc.org/NET/ssnx/ssn#observes</a>
     */
    public static final Property SSN_OBSERVES = ResourceFactory.createProperty(SSN_NAMESPACE, "observes");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#madeObservation">
     *   http://purl.oclc.org/NET/ssnx/ssn#madeObservation
     * </a>
     */
    public static final Property SSN_MADE_OBSERVATION =
                ResourceFactory.createProperty(SSN_NAMESPACE, "madeObservation");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest">
     *   http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest
     * </a>
     */
    public static final Property SSN_FEATURE_OF_INTEREST_PROPERTY =
                ResourceFactory.createProperty(SSN_NAMESPACE, "featureOfInterest");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#observedProperty">
     *   http://purl.oclc.org/NET/ssnx/ssn#observedProperty
     * </a>
     */
    public static final Property SSN_OBSERVED_PROPERTY =
                ResourceFactory.createProperty(SSN_NAMESPACE, "observedProperty");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#observationResult">
     *   http://purl.oclc.org/NET/ssnx/ssn#observationResult
     * </a>
     */
    public static final Property SSN_OBSERVATION_RESULT =
                ResourceFactory.createProperty(SSN_NAMESPACE, "observationResult");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#isProducedBy">
     *   http://purl.oclc.org/NET/ssnx/ssn#isProducedBy
     * </a>
     */
    public static final Property SSN_IS_PRODUCED_BY =
                ResourceFactory.createProperty(SSN_NAMESPACE, "isProducedBy");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#hasValue">
     *   http://purl.oclc.org/NET/ssnx/ssn#hasValue
     * </a>
     */
    public static final Property SSN_HAS_VALUE =
                ResourceFactory.createProperty(SSN_NAMESPACE, "hasValue");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#hasProperty">
     *   http://purl.oclc.org/NET/ssnx/ssn#hasProperty
     * </a>
     */
    public static final Property SSN_HAS_PROPERTY =
                ResourceFactory.createProperty(SSN_NAMESPACE, "hasProperty");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#Observation">
     *   http://purl.oclc.org/NET/ssnx/ssn#Observation
     * </a>
     */
    public static final Resource SSN_OBSERVATION =
                ResourceFactory.createResource(SSN_NAMESPACE + "Observation");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#SensorOutput">
     *   http://purl.oclc.org/NET/ssnx/ssn#SensorOutput
     * </a>
     */
    public static final Resource SSN_SENSOR_OUTPUT =
                ResourceFactory.createResource(SSN_NAMESPACE + "SensorOutput");

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#FeatureOfInterest">
     *   http://purl.oclc.org/NET/ssnx/ssn#FeatureOfInterest
     * </a>
     */
    public static final Resource SSN_FEATURE_OF_INTEREST =
                ResourceFactory.createResource(SSN_NAMESPACE + "FeatureOfInterest");

    /**
     * The name of the SPARQL parameter (?val) which is considered to contain the observation value
     */
    public static final String VALUE_PARAM = "?val";


    private static final Map<String, String> DEFAULT_PREFIX_MAPPING = new HashMap<>(3);
    static{
        DEFAULT_PREFIX_MAPPING.put("http://purl.oclc.org/NET/ssnx/ssn#", "ssn");
        DEFAULT_PREFIX_MAPPING.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");

    }

    private ScheduledExecutorService internalExecutor;
    private ScheduledFuture observationFuture;
    private Channel localChannel;

    private final URI sensorName;
    private final URI sensorType;
    private final URI featureOfInterest;
    private final URI observedProperty;

    private ObservationValue observationValue;

    private Query query;


    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.DataOrigin}
     *
     * @param sensorName the identifier for this {@link eu.spitfire.ssp.backend.generic.DataOrigin}
     */
    public VirtualSensor(final URI sensorName, final URI sensorType, final URI featureOfInterest,
        final URI observedProperty, final Query query, final Channel localChannel,
            ScheduledExecutorService internalExecutor) throws Exception{

        //the graph name is the same URI as the sensor name!
        super(sensorName, sensorName);

        this.internalExecutor = internalExecutor;
        this.localChannel = localChannel;
        this.sensorName = sensorName;
        this.sensorType = sensorType;
        this.featureOfInterest = featureOfInterest;
        this.observedProperty = observedProperty;

        this.observationValue = new ObservationValue(null);
        this.observationFuture = null;

        this.query = query;
    }

    /**
     * Returns a {@link org.apache.jena.rdf.model.Model} containing the description of the sensor itself as well as its
     * latest observation
     *
     * @return a {@link org.apache.jena.rdf.model.Model} containing the description of the sensor itself as well as its
     * latest observation
     */
    public Model createGraphAsModel(){

        Model model = ModelFactory.createDefaultModel();

        // set prefix mappings
        addPrefixMappings(model);

        // create resources
        Resource sensorName = model.createResource(this.sensorName.toString());
        Resource sensorType = model.createResource(this.sensorType.toString());
        Resource featureOfInterest = model.createResource(this.featureOfInterest.toString());
        Property observedProperty = model.createProperty(this.observedProperty.toString());
//        Resource observation = model.createResource(this.sensorName.toString() + "-Observation");
        Resource result = model.createResource(this.sensorName.toString() + "-Result");

        // sensor statement
        sensorName.addProperty(RDF_TYPE, sensorType);
        sensorName.addProperty(SSN_OBSERVES, observedProperty);

        if(this.observationValue == null){
            return model;
        }

        //one more sensor statements (only if there is an observation value)
        sensorName.addProperty(SSN_MADE_OBSERVATION, model.createResource()
            // observation statements
            .addProperty(RDF_TYPE, SSN_OBSERVATION)
            .addProperty(SSN_FEATURE_OF_INTEREST_PROPERTY, featureOfInterest)
            .addProperty(SSN_OBSERVED_PROPERTY, observedProperty)
            .addProperty(SSN_OBSERVATION_RESULT, model.createResource()

                            // result statements
                            .addProperty(RDF_TYPE, SSN_SENSOR_OUTPUT)
                            .addProperty(SSN_IS_PRODUCED_BY, sensorName)
                            .addProperty(SSN_HAS_VALUE, this.observationValue.getValue())
            ));


        // add inferred statements about FOI
//        featureOfInterest.addProperty(RDF_TYPE, SSN_FEATURE_OF_INTEREST);
//        featureOfInterest.addProperty(SSN_HAS_PROPERTY, observedProperty);
        featureOfInterest.addProperty(observedProperty, this.observationValue.getValue());

        return model;
    }


    // adds the necessary prefix mappings to the given model
    private void addPrefixMappings(Model model){

        Map<String, String> prefixes = new HashMap<>(DEFAULT_PREFIX_MAPPING);
        int i = 0;

        // virtual sensor instance
        String namespace = SplitIRI.namespace(sensorName.toString());
        prefixes.put(namespace, "ssp");

        // type
        namespace = SplitIRI.namespace(sensorType.toString());
        if(!prefixes.containsKey(namespace)){
            prefixes.put(namespace, "ns" + (i++));
        }

        // feature of interest
        namespace = SplitIRI.namespace(featureOfInterest.toString());
        if(!prefixes.containsKey(namespace)){
            prefixes.put(namespace, "ns" + (i++));
        }

        // observed property
        namespace = SplitIRI.namespace(observedProperty.toString());
        if(!prefixes.containsKey(namespace)){
            prefixes.put(namespace, "ns" + (i));
        }

        // xsd (if necessary)
        if(this.observationValue.getValue().isLiteral()
            && ((Literal) this.observationValue.getValue()).getDatatype() != null
            && ((Literal) this.observationValue.getValue()).getDatatypeURI().startsWith(XSD_NAMESAPCE)){

                prefixes.put(XSD_NAMESAPCE, "xsd");
        }

        // add prefixes to model
        for(Map.Entry<String, String> entry : prefixes.entrySet()){
            model.setNsPrefix(entry.getValue(), entry.getKey());
        }
    }


    /**
     * Runs the {@link org.apache.jena.query.Query} to create a new observation value
     *
     * @return A {@link com.google.common.util.concurrent.ListenableFuture} containing the duration of the query
     * execution in milliseconds.
     */
    public ListenableFuture<Long> makeSingleObservation(){

        // request the query execution
        InternalQueryExecutionRequest executionRequest = new InternalQueryExecutionRequest(query);
        ListenableFuture<QueryExecutionResults> resultsFuture =
            executionRequest.getResultsFuture();
        Channels.write(this.localChannel, executionRequest);

        // await and process the result of the query execution
        SettableFuture<Long> updateFuture = SettableFuture.create();
        Futures.addCallback(resultsFuture, new FutureCallback<QueryExecutionResults>() {
            @Override
            public void onSuccess(QueryExecutionResults results) {
                try {
                    ResultSet resultSet = results.getResultSet();

                    if (resultSet.hasNext()) {
                        QuerySolution querySolution = resultSet.nextSolution();
                        if (querySolution.contains(VALUE_PARAM)) {
                            RDFNode node = querySolution.get(VALUE_PARAM);
                            if(node.isLiteral() && ((Literal) node).getDatatype() != null){
                                observationValue.setValue(ResourceFactory.createTypedLiteral(
                                        ((Literal) node).getLexicalForm(), ((Literal) node).getDatatype()
                                ));
                            }
                            else{
                                observationValue.setValue(node);
                            }
                        }
                        else {
                            observationValue.setValue(ResourceFactory.createPlainLiteral("null"));
                        }
                    }
                    else {
                        observationValue.setValue(ResourceFactory.createPlainLiteral("null"));
                    }

                    LOG.info("Succesfully updated observation value for sensor: {}", sensorName.toString());
                    updateFuture.set(results.getDuration());
                }
                catch(Exception ex){
                    LOG.error("Exception while processing Query Result!", ex);
                    updateFuture.setException(ex);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Exception while processing Query !", throwable);
                updateFuture.setException(throwable);
            }
        });

        return updateFuture;
    }

    /**
     * Returns <code>true</code>
     * @return <code>true</code>
     */
    @Override
    public boolean isObservable() {
        return true;
    }


    @Override
    public boolean shutdown(){
        boolean result = false;
        if(this.observationFuture != null){
            result = this.observationFuture.cancel(true);
        }

        if(result){
            LOG.info("Virtual Sensor " + this.getGraphName() + " shut down!");
        }
        else{
            LOG.error("Failed to shut down Virtual Sensor " + this.getGraphName() + "!");
        }
        return result;
    }

    /**
     * Returns the {@link org.apache.jena.query.Query} which is used to retrieve the actual sensor value.
     * @return the {@link org.apache.jena.query.Query} which is used to retrieve the actual sensor value.
     */
    public Query getQuery() {
        return this.query;
    }

    @Override
    public int hashCode() {
        return this.getGraphName().hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if(object == null || !(object instanceof VirtualSensor))
            return false;

        VirtualSensor other = (VirtualSensor) object;
        return other.getGraphName().equals(this.getGraphName());
    }

    /**
     * Adds the given {@link VirtualSensorsObserver} as {@link java.util.Observer}
     * of this sensors {@link VirtualSensor.ObservationValue}.
     *
     * @param observer the {@link VirtualSensorsObserver} to be added as
     * {@link java.util.Observer} of this sensors
     * {@link VirtualSensor.ObservationValue}.
     */
    public void addObserver(VirtualSensorsObserver observer){
        this.observationValue.addObserver(observer);
    }

    /**
     * Makes this {@link VirtualSensor} to periodically execute its
     * {@link org.apache.jena.query.Query} and thus update its
     * {@link VirtualSensor.ObservationValue}.
     *
     * @param frequency the frequency of the periodic {@link org.apache.jena.query.Query} execution
     * @param timeUnit the {@link java.util.concurrent.TimeUnit} of the given frequency
     */
    public void startPeriodicObservations(int frequency, TimeUnit timeUnit){
        this.observationFuture = internalExecutor.scheduleAtFixedRate(new ObservationTask(), 0, frequency, timeUnit);
        LOG.info("Virtual sensor \"{}\" started periodic observations!");
    }


    private class ObservationTask implements Runnable {
        @Override
        public void run() {
            Futures.addCallback(makeSingleObservation(), new FutureCallback<Long>() {
                @Override
                public void onSuccess(Long duration) {
                    LOG.info("Finished periodic observation of {} (exec-duration: {})", getGraphName(), duration);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("This should never happen!)", throwable);
                }
            });
        }
    }


    /**
     * Internal representation of a {@link VirtualSensor}s measurement which
     * extends {@link java.util.Observable} to be observable by instances of
     * {@link VirtualSensorsObserver}.
     */
    public class ObservationValue extends Observable{

        private RDFNode value;

        public ObservationValue(RDFNode value){
            this.value = value;
        }

        public void setValue(RDFNode value){
            if(this.value == null && value == null){
                return;
            }

            if(this.value == null || value == null || !this.value.equals(value)){
                LOG.info("Observation value of {} changed to {}.", VirtualSensor.this.getGraphName(), value);
                this.value = value;
                setChanged();
                notifyObservers(VirtualSensor.this);
            }
        }

        public RDFNode getValue(){
            return this.value;
        }
    }


//    private static final String DELETE_QUERY_TEMPLATE =
//            "DELETE {\n\t" +
//                    "GRAPH <%s> { ?s ?p ?o }\n\t" +
//                    "?s ?p ?o\n" +
//                    "} WHERE {\n\t" +
//                    "GRAPH <%s> {?s ?p ?o }\n" +
//                    "}";


//    private static final String UPDATE_QUERY_TEMPLATE =
//            "PREFIX vs: <http://example.org/virtual-sensors#>\n" +
//            "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
//            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
//            "\n" +
//            "DELETE {\n\t" +
//                "GRAPH <%s> { ?s ssn:hasValue ?o }\n\t" +
//                "?s ssn:hasValue ?o\n" +
//            "} INSERT {\n\t" +
//                "GRAPH <%s> { ?s ssn:hasValue %s }\n\t" +
//                "?s ssn:hasValue %s\n" +
//            "} WHERE {\n\t" +
//                "GRAPH <%s> { vs:%s ssn:hasValue ?o }\n\t" +
//            "}";


//    private static String createUpdateQuery(URI graphName, String sensorName, String sensorValue){
//        return String.format(Locale.ENGLISH, UPDATE_QUERY_TEMPLATE, graphName.toString(), sensorName, sensorName,
//                graphName.toString(), sensorName, sensorValue, sensorName, sensorValue, graphName, sensorName);
//    }



//    private class VirtualSensorUpdateTask implements Runnable{
//
//        private Query query;
//        private Channel localChannel;
//
//        private VirtualSensorUpdateTask(Query query, Channel localChannel){
//            this.query = query;
//            this.localChannel = localChannel;
//        }
//
//        private Query getQuery(){
//            return this.query;
//        }
//
//        @Override
//        public void run() {
//            SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();
//            InternalQueryExecutionRequest internalQueryExecutionRequest = new InternalQueryExecutionRequest(query);
//            Channels.write(localChannel, internalQueryExecutionRequest);
//
//            //Await the result of the query execution
//            Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
//                @Override
//                public void onSuccess(ResultSet resultSet) {
//
//                    Literal sensorValue;
//
//                    if (resultSet.hasNext()) {
//                        QuerySolution querySolution = resultSet.nextSolution();
//                        if(querySolution.contains("?aggVal")){
//                            sensorValue = querySolution.getLiteral("?aggVal");
//                        }
//                        else{
//                            sensorValue = ResourceFactory.createTypedLiteral(0.0);
//                        }
//                    }
//                    else{
//                        sensorValue = ResourceFactory.createTypedLiteral(0.0);
//                    }
//
//
//                    //Update sensor value in cache
//                    String sensorName = getIdentifier().getRawFragment();
//                    String value = "\"" + sensorValue.getLexicalForm() + "\"^^xsd:double";
//
//                    String updateQuery = createUpdateQuery(getGraphName(), sensorName, value);
//
//                    LOG.info(updateQuery);
//
//                    UpdateRequest updateRequest = UpdateFactory.create(updateQuery);
//                    InternalUpdateRequest internalUpdateRequest = new InternalUpdateRequest(updateRequest);
//
//                    Futures.addCallback(internalUpdateRequest.getUpdateFuture(), new FutureCallback<Void>() {
//                        @Override
//                        public void onSuccess(Void v) {
//                            LOG.info("Updated Virtual Sensor Value!");
//                        }
//
//                        @Override
//                        public void onFailure(Throwable throwable) {
//                            LOG.error("Failed to update Virtual Sensor Value!", throwable);
//                        }
//                    });
//
//                    Channels.write(localChannel, internalUpdateRequest);
//                }
//
//                @Override
//                public void onFailure(Throwable throwable) {
//                    LOG.error("Failed to retrieve Virtual Sensor Value!", throwable);
//                }
//            });
//        }
//    }


}
