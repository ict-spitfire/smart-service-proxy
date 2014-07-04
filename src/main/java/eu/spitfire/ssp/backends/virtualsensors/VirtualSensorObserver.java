package eu.spitfire.ssp.backends.virtualsensors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.Observer;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 20.06.14.
 */
public class VirtualSensorObserver extends Observer<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private VirtualSensorBackendComponentFactory componentFactory;
    private LocalServerChannel localChannel;
    private ScheduledExecutorService internalTaskExecutor;

    protected VirtualSensorObserver(VirtualSensorBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.componentFactory = componentFactory;
        this.localChannel = componentFactory.getLocalChannel();
        this.internalTaskExecutor = componentFactory.getInternalTasksExecutor();
    }

    @Override
    public void startObservation(final DataOrigin<URI> dataOrigin) {
        VirtualSensor virtualSensor = (VirtualSensor) dataOrigin;
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
                ListenableFuture<GraphStatusMessage> cachedStatusFuture = this.getCachedStatus();
                ListenableFuture<RDFNode> sensorValueFuture = this.getActualSensorValue();

                ListenableFuture<List<Object>> combinedFuture = Futures.allAsList(cachedStatusFuture, sensorValueFuture);

                Futures.addCallback(combinedFuture, new FutureCallback<List<Object>>() {

                    @Override
                    public void onSuccess(List<Object> resultList) {
                        try{
                            Model vsModel =
                                    ((ExpiringNamedGraphStatusMessage) resultList.get(0)).getExpiringGraph().getGraph();

                            RDFNode sensorValue = ((RDFNode) resultList.get(1));

                            Statement valueStatement = vsModel.getProperty(
                                    vsModel.getResource(virtualSensor.getGraphName().toString()),
                                    vsModel.getProperty("http://spitfire-project.eu/ontology/ns/value")
                            );

                            if(!valueStatement.getObject().equals(sensorValue)){
                                valueStatement.changeObject(sensorValue);

                                ExpiringNamedGraphStatusMessage statusMessage = new ExpiringNamedGraphStatusMessage(
                                        new ExpiringNamedGraph(virtualSensor.getGraphName(), vsModel)
                                );

                                Channels.write(localChannel, statusMessage);

                            }

                        }
                        catch(Exception ex){
                            log.error("Exception while observing virtual sensor (Graph: {})",
                                    virtualSensor.getGraphName(), ex);
                        }
                        finally{
                            internalTaskExecutor.execute(new VirtualSensorObservation(virtualSensor));
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Exception during observation of virtual sensor (Graph: {})",
                                virtualSensor.getGraphName(), t);

                        internalTaskExecutor.execute(new VirtualSensorObservation(virtualSensor));
                    }
                });
            }

            catch(Exception ex){
                log.error("Could not start observation of graph {}", virtualSensor.getGraphName(), ex);
            }
        }

        private ListenableFuture<GraphStatusMessage> getCachedStatus(){
            VirtualSensorAccessor accessor = componentFactory.getAccessor(virtualSensor);
            return accessor.getStatus(virtualSensor);
        }


        private ListenableFuture<RDFNode> getActualSensorValue(){
            final SettableFuture<RDFNode> sensorValueFuture = SettableFuture.create();

            //Send SPARQL query
            Query sparqlQuery = virtualSensor.getSparqlQuery();
            SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();
            Channels.write(
                    componentFactory.getLocalChannel(), new SparqlQueryMessage(sparqlQuery, queryResultFuture)
            );

            Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
                @Override
                public void onSuccess(ResultSet resultSet) {
                    try{
                        sensorValueFuture.set(resultSet.nextSolution().get("?aggValue"));
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
//                this.getComponentFactory().getLocalChannel(), new SparqlQueryMessage(sparqlQuery, queryResultFuture)
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
//                        vsModel.createResource(virtualSensor.getGraphName().toString()),
//                        vsModel.createProperty("http://spitfire-project.eu/ontology/ns/value"),
//                        sensorValue
//                );
//
//                vsModel.add(statement);
//
//                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(virtualSensor.getGraphName(), vsModel,
//                        new Date(System.currentTimeMillis() + Accessor.MILLIS_PER_YEAR));
//
//                ExpiringNamedGraphStatusMessage statusMessage = new ExpiringNamedGraphStatusMessage(expiringNamedGraph);
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
