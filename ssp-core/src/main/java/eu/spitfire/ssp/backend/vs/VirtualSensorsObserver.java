package eu.spitfire.ssp.backend.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backend.generic.ComponentFactory;
import eu.spitfire.ssp.backend.generic.DataOriginObserver;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Oliver Kleine
 */
public class VirtualSensorsObserver extends DataOriginObserver<URI, VirtualSensor> implements Observer {

    private static Logger LOG = LoggerFactory.getLogger(VirtualSensorsObserver.class.getName());

    protected VirtualSensorsObserver(ComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
    }

    /**
     * Registers this instance of {@link VirtualSensorsObserver} at the
     * {@link VirtualSensor} and calls
     * {@link VirtualSensor#startPeriodicObservations(int, java.util.concurrent.TimeUnit}
     *
     * <b>Note:</b> The {@link java.util.Observable} is (for internal reasons) not the
     * {@link VirtualSensor } but its
     * {@link VirtualSensor.ObservationValue}
     *
     * @param virtualSensor the {@link VirtualSensor } whose
     * {@link VirtualSensor.ObservationValue} is to be observed
     */
    @Override
    public void startObservation(final VirtualSensor virtualSensor) {
        virtualSensor.addObserver(this);
        virtualSensor.startPeriodicObservations(30, TimeUnit.SECONDS);
    }

    /**
     * This method is called by instances of {@link VirtualSensor.ObservationValue}.
     *
     * @param observable an instance of {@link VirtualSensor.ObservationValue}
     *
     * @param object the instance of {@link VirtualSensor} which provides the
     * {@link VirtualSensor.ObservationValue} that called this method
     */
    @Override
    public void update(Observable observable, Object object) {
        if(!(object instanceof VirtualSensor)){
            LOG.error("This should never happen (Object was no instance of VirtualSensor)!");
            return;
        }

        VirtualSensor virtualSensor = (VirtualSensor) object;

        // create new expiring named graph
        ExpiringNamedGraph graph = new ExpiringNamedGraph(
            virtualSensor.getGraphName(), virtualSensor.createGraphAsModel()
        );

        // update the cache
        ListenableFuture<Void> updateFuture = updateCache(graph);
        Futures.addCallback(updateFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                LOG.info("Succesfully updated virtual sensor: {}", virtualSensor.getGraphName());
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Failed to update virtual sensor: {}", virtualSensor.getGraphName());
            }
        });
    }


//    private class VirtualSensorObservation implements Runnable{
//
//        private VirtualSensor virtualSensor;
//
//        private VirtualSensorObservation(VirtualSensor virtualSensor){
//            this.virtualSensor = virtualSensor;
//        }
//
//        @Override
//        public void run() {
//            try{
//                ListenableFuture<DataOriginInquiryResult> statusFuture = this.getCachedStatus();
//                ListenableFuture<RDFNode> sensorValueFuture = this.getActualSensorValue();
//
//                ListenableFuture<List<Object>> combinedFuture = Futures.allAsList(statusFuture, sensorValueFuture);
//                Futures.addCallback(combinedFuture, new FutureCallback<List<Object>>() {
//
//                    @Override
//                    public void onSuccess(List<Object> resultList) {
//                        try{
//                            Model vsModel = ((ExpiringGraph) resultList.get(0)).getModel();
//                            RDFNode sensorValue = ((RDFNode) resultList.get(1));
//
//                            Resource subject = vsModel.getResource(virtualSensor.getGraphName() + "-SensorOutput");
//
//                            Statement valueStatement = subject.getProperty(
//                                    vsModel.getProperty("http://purl.oclc.org/NET/ssnx/ssn#", "hasValue")
//                            );
//
//                            if(valueStatement != null){
//                                Literal cachedSensorValue = ResourceFactory.createTypedLiteral(
//                                        valueStatement.getObject().toString(), XSDDatatype.XSDinteger
//                                );
//
//                                if(!cachedSensorValue.equals(sensorValue)){
//                                    valueStatement.changeObject(sensorValue);
//                                    updateCache(new ExpiringNamedGraph(virtualSensor.getGraphName(), vsModel));
//                                }
//
//                            }
//
//                        }
//                        catch(Exception ex){
//                            log.error("Exception while observing virtual sensor (Graph: {})",
//                                    virtualSensor.getGraphName(), ex);
//                        }
//                        finally{
//                            internalTaskExecutor.schedule(
//                                    new VirtualSensorObservation(virtualSensor), 10, TimeUnit.HOURS
//                            );
//                        }
//                    }
//
//                    @Override
//                    public void onFailure(Throwable t) {
//                        log.error("Exception during observation of virtual sensor (Graph: {})",
//                                virtualSensor.getGraphName(), t);
//
//                        internalTaskExecutor.schedule(
//                                new VirtualSensorObservation(virtualSensor), 1, TimeUnit.MINUTES
//                        );
//                    }
//                });
//            }
//
//            catch(Exception ex){
//                log.error("Could not start observation of graph {}", virtualSensor.getGraphName(), ex);
//            }
//        }
//
//
//        private ListenableFuture<DataOriginInquiryResult> getCachedStatus(){
//            VirtualSensorsAccessor accessor = (VirtualSensorsAccessor) componentFactory.getAccessor(virtualSensor);
//
//            return accessor.getStatus(virtualSensor);
//        }
//
//
//        private ListenableFuture<RDFNode> getActualSensorValue(){
//            final SettableFuture<RDFNode> sensorValueFuture = SettableFuture.create();
//
//            //Send SPARQL query
//            Query query = virtualSensor.getQuery();
//            InternalQueryExecutionRequest executionRequest = new InternalQueryExecutionRequest(query);
//
//            Channels.write(
//                    componentFactory.getLocalChannel(), new InternalQueryExecutionRequest(query)
//            );
//
//
//            ListenableFuture<QueryExecutionResults> resultsFuture =
//                    executionRequest.getResultsFuture();
//
//            Futures.addCallback(resultsFuture, new FutureCallback<QueryExecutionResults>() {
//                @Override
//                public void onSuccess(QueryExecutionResults results) {
//                    try{
//                        ResultSet resultSet = results.getResultSet();
//                        sensorValueFuture.set(resultSet.nextSolution().get("?aggVal"));
//                    }
//                    catch(Exception ex){
//                        log.error("Excpetion during retrieval of virtual sensor value (Graph: {})",
//                                virtualSensor.getGraphName(), ex);
//
//                        sensorValueFuture.setException(ex);
//                    }
//                }
//
//                @Override
//                public void onFailure(Throwable t) {
//                    log.error("Excpetion during retrieval of virtual sensor value (Graph: {})",
//                            virtualSensor.getGraphName(), t);
//
//                    sensorValueFuture.setException(t);
//                }
//
//            });
//
//            return sensorValueFuture;
//        }
//    }
}



//        Query sparqlQuery = ((VirtualSensor) virtualSensor).getQuery();
//        SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();

//        //Send SPARQL query
//        Channels.write(
//                this.getComponentFactory().getLocalChannel(), new QueryTask(sparqlQuery, queryResultFuture)
//        );
//
//
//        Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
//            @Override
//            public void onSuccess(ResultSet resultSet) {
//                Model vsModel = ModelFactory.createDefaultModel();
//
//                //Get the first row of the result set
//                RDFNode sensorValue;
//
//                if(resultSet.hasNext()){
//                    QuerySolution querySolution = resultSet.nextSolution();
//                    sensorValue = querySolution.get(querySolution.varNames().next());
//                }
//
//                else{
//                    sensorValue = ResourceFactory.createTypedLiteral("0", XSDDatatype.XSDinteger);
//                }
//
//                Statement statement  = vsModel.createStatement(
//                        vsModel.createResource(virtualSensor.createGraphName().toString()),
//                        vsModel.createProperty("http://spitfire-project.eu/ontology/ns/value"),
//                        sensorValue
//                );
//
//                vsModel.add(statement);
//
//                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(virtualSensor.createGraphName(), vsModel,
//                        new Date(System.currentTimeMillis() + Accessor.MILLIS_PER_CENTURY));
//
//                ExpiringNamedGraphHttpResponse statusMessage = new ExpiringNamedGraphHttpResponse(expiringNamedGraph);
//
//                graphStatusFuture.set(statusMessage);
//
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                graphStatusFuture.setException(t);
//            }
//        });
//}
