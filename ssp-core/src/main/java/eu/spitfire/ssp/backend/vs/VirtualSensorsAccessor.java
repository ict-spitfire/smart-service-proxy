package eu.spitfire.ssp.backend.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.generic.Accessor;
import eu.spitfire.ssp.backend.generic.ComponentFactory;
import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorsAccessor extends Accessor<URI, VirtualSensor> {

    private static Logger LOG = LoggerFactory.getLogger(VirtualSensorsAccessor.class.getName());


    protected VirtualSensorsAccessor(ComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
    }


    @Override
    public ListenableFuture<ExpiringNamedGraph> getStatus(final VirtualSensor virtualSensor) {
        LOG.info("Try to get status for virtual sensor: {}", virtualSensor.getIdentifier());

        final SettableFuture<ExpiringNamedGraph> graphFuture = SettableFuture.create();

        // await observation result
        Futures.addCallback(virtualSensor.makeSingleObservation(), new FutureCallback<Long>() {
            @Override
            public void onSuccess(Long duration) {
                LOG.info("Status retrieval for {} took {} millis.", virtualSensor.getIdentifier(), duration);
                ExpiringNamedGraph graph = new ExpiringNamedGraph(
                        virtualSensor.getGraphName(), virtualSensor.createGraphAsModel()
                );
                graphFuture.set(graph);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Status retrieval for {} failed!", virtualSensor.getIdentifier(), throwable);
                graphFuture.setException(throwable);
            }
        });

        return graphFuture;
    }

    @Override
    public ListenableFuture<ModificationResult> deleteResource(VirtualSensor virtualSensor){
        SettableFuture<ModificationResult> resultFuture = SettableFuture.create();
        URI graphName = virtualSensor.getGraphName();

        if(!virtualSensor.shutdown()){
            resultFuture.setException(
                new RuntimeException("Failed to shutdown virtual sensor: " + graphName)
            );
        }
        else{
            Futures.addCallback(this.getComponentFactory().getRegistry().unregisterDataOrigin(graphName),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        resultFuture.set(ModificationResult.DELETED);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        LOG.error("Error during deregistration of virtual sensor \"{}\".", graphName, throwable);
                        resultFuture.setException(throwable);
                    }
                }
            );
        }

        return resultFuture;
    }
}

////        String query = String.format("SELECT ?s ?p ?o FROM <%s> WHERE {?s ?p ?o}", virtualSensor.getGraphName());
//
//
//                Query query = QueryFactory.create(String.format(
//                        "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
//                                "SELECT ?value WHERE {<%s-SensorOutput> ssn:hasValue ?value }", virtualSensor.getGraphName()
//                ));
//
//
//        InternalQueryExecutionRequest executionRequest = new InternalQueryExecutionRequest(query);
//        ChannelFuture channelFuture = Channels.write(
//                this.getComponentFactory().getLocalChannel(), executionRequest
//        );
//
//
//        channelFuture.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture future) throws Exception {
//                if(!future.isSuccess()){
//                    log.error("Exception during retrieval of virtual sensor status (Graph: {})!",
//                            virtualSensor.getGraphName(), future.getCause());
//                }
//            }
//        });
//
//
//
//        ListenableFuture<QueryExecutionResults> resultsFuture =  executionRequest.getResultsFuture();
//
//        Futures.addCallback(resultsFuture, new FutureCallback<QueryExecutionResults>() {
//            @Override
//            public void onSuccess(QueryExecutionResults executionResults) {
//                ResultSet resultSet = executionResults.getResultSet();
//
//                if (resultSet == null || !resultSet.hasNext()) {
//                    graphFuture.setException(new NamedGraphNotFoundException(
//                            String.format("Graph %s not found!", virtualSensor.getGraphName())
//                    ));
//                    return;
//                }
//
//                Model model = Converter.toModel(resultSet);
//
////                Model vsModel = ModelFactory.createDefaultModel();
////
////                while (resultSet.hasNext()) {
////                    Binding binding = resultSet.nextBinding();
////
////                    Node subject = binding.get(Var.alloc("s"));
////                    Node predicate = binding.get(Var.alloc("p"));
////                    Node object = binding.get(Var.alloc("o"));
////
////                    Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
////                            ResourceFactory.createResource(subject.getURI());
////
////                    Property p = ResourceFactory.createProperty(predicate.getURI());
////
////                    RDFNode o = object.isBlank() ? ResourceFactory.createResource(object.getBlankNodeLabel()) :
////                            object.isLiteral() ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm()) :
////                                    ResourceFactory.createResource(object.getURI());
////
////                    vsModel.add(vsModel.createStatement(s, p, o));
////                }
//
//                graphFuture.set(new ExpiringNamedGraph(virtualSensor.getGraphName(), model));
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                log.error("Failed to retrieve virtual sensor status (Graph: {})", virtualSensor.getGraphName(), t);
//                graphFuture.setException(t);
//            }
//        });
//
//        return graphFuture;
//    }

