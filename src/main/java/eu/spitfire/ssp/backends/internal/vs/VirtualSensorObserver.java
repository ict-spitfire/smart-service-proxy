package eu.spitfire.ssp.backends.internal.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.DataOriginAccessResult;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.internal.se.SemanticEntity;
import eu.spitfire.ssp.backends.internal.se.SemanticEntityBackendComponentFactory;
import eu.spitfire.ssp.server.internal.messages.AccessResult;
import eu.spitfire.ssp.backends.generic.Observer;
import eu.spitfire.ssp.server.common.messages.QueryTask;
import eu.spitfire.ssp.server.internal.messages.ExpiringGraph;
import eu.spitfire.ssp.server.internal.messages.ExpiringNamedGraph;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by olli on 20.06.14.
 */
public class VirtualSensorObserver extends Observer<URI, VirtualSensor> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private BackendComponentFactory<URI, VirtualSensor> componentFactory;
    private ScheduledExecutorService internalTaskExecutor;

    protected VirtualSensorObserver(BackendComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
        this.componentFactory = componentFactory;
        this.internalTaskExecutor = componentFactory.getInternalTasksExecutor();
    }

    @Override
    public void startObservation(final VirtualSensor virtualSensor) {
        this.internalTaskExecutor.execute(new VirtualSensorObservation(virtualSensor));
    }



    private class VirtualSensorObservation implements Runnable{

        private VirtualSensor virtualSensor;

        private VirtualSensorObservation(VirtualSensor virtualSensor){
            this.virtualSensor = virtualSensor;
        }

        @Override
        public void run() {
            try{
                ListenableFuture<DataOriginAccessResult> cachedStatusFuture = this.getCachedStatus();
                ListenableFuture<RDFNode> sensorValueFuture = this.getActualSensorValue();

                ListenableFuture<List<Object>> combinedFuture = Futures.allAsList(cachedStatusFuture, sensorValueFuture);

                Futures.addCallback(combinedFuture, new FutureCallback<List<Object>>() {

                    @Override
                    public void onSuccess(List<Object> resultList) {
                        try{
                            Model vsModel = ((ExpiringGraph) resultList.get(0)).getGraph();
                            RDFNode sensorValue = ((RDFNode) resultList.get(1));

                            Statement valueStatement = vsModel.getProperty(
                                    vsModel.getResource(virtualSensor.getGraphName().toString()),
                                    vsModel.getProperty("http://spitfire-project.eu/ontology/ns/value")
                            );

                            if(valueStatement != null){
                                Literal cachedSensorValue = ResourceFactory.createTypedLiteral(
                                        valueStatement.getObject().toString(), XSDDatatype.XSDinteger
                                );

                                if(!cachedSensorValue.equals(sensorValue)){
                                    valueStatement.changeObject(sensorValue);

                                    updateCache(new ExpiringNamedGraph(virtualSensor.getGraphName(), vsModel));
                                }

                            }

                        }
                        catch(Exception ex){
                            log.error("Exception while observing virtual sensor (Graph: {})",
                                    virtualSensor.getGraphName(), ex);
                        }
                        finally{
                            internalTaskExecutor.schedule(
                                    new VirtualSensorObservation(virtualSensor), 5, TimeUnit.SECONDS
                            );
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Exception during observation of virtual sensor (Graph: {})",
                                virtualSensor.getGraphName(), t);

                        internalTaskExecutor.schedule(
                                new VirtualSensorObservation(virtualSensor), 5, TimeUnit.SECONDS
                        );
                    }
                });
            }

            catch(Exception ex){
                log.error("Could not start observation of graph {}", virtualSensor.getGraphName(), ex);
            }
        }


        private ListenableFuture<DataOriginAccessResult> getCachedStatus(){
            VirtualSensorAccessor accessor = (VirtualSensorAccessor) componentFactory.getAccessor(virtualSensor);

            return accessor.getStatus(virtualSensor);
        }


        private ListenableFuture<RDFNode> getActualSensorValue(){
            final SettableFuture<RDFNode> sensorValueFuture = SettableFuture.create();

            //Send SPARQL query
            Query sparqlQuery = virtualSensor.getSparqlQuery();
            SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();
            Channels.write(
                    componentFactory.getLocalChannel(), new QueryTask(sparqlQuery, queryResultFuture)
            );

            Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
                @Override
                public void onSuccess(ResultSet resultSet) {
                    try{
                        sensorValueFuture.set(resultSet.nextSolution().get("?aggVal"));
                    }
                    catch(Exception ex){
                        log.error("Excpetion during retrieval of virtual sensor value (Graph: {})",
                                virtualSensor.getGraphName(), ex);

                        sensorValueFuture.setException(ex);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Excpetion during retrieval of virtual sensor value (Graph: {})",
                            virtualSensor.getGraphName(), t);

                    sensorValueFuture.setException(t);
                }

            });

            return sensorValueFuture;
        }
    }
}



//        Query sparqlQuery = ((VirtualSensor) virtualSensor).getSparqlQuery();
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
