package eu.spitfire.ssp.backends.internal.se;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
import eu.spitfire.ssp.backends.generic.Accessor;

import java.net.URI;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntityAccessor extends Accessor<URI, SemanticEntity> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.Accessor}
     *
     * @param componentFactory
     */
    protected SemanticEntityAccessor(BackendComponentFactory<URI, SemanticEntity> componentFactory) {
        super(componentFactory);
    }


    @Override
    public ListenableFuture<DataOriginInquiryResult> getStatus(SemanticEntity semanticEntity){
        SettableFuture<DataOriginInquiryResult> accessResultFuture = SettableFuture.create();

        accessResultFuture.set(new DataOriginAccessError(
                AccessResult.Code.INTERNAL_ERROR,
                "This GET request should have been answered from the cache!"
        ));

        return accessResultFuture;
    }

//




//    @Override
//    public ListenableFuture<DataOriginInquiryResult> getStatus(SemanticEntity semanticEntity){
//        SettableFuture<DataOriginInquiryResult> accessResultFuture = SettableFuture.create();
//
//        log.info("Try to get status for data origin with identifier {}", dataOrigin.getIdentifier());
//
//        final SettableFuture<AccessResult> graphStatusFuture = SettableFuture.create();
//
//        Query sparqlQuery = QueryFactory.create(
//                String.format("SELECT ?s ?p ?o FROM <%s> WHERE {?s ?p ?o}", dataOrigin.getGraphName())
//        );
//
//        SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();
//
//        ChannelFuture channelFuture = Channels.write(
//                this.localChannel, new QueryTask(sparqlQuery, resultSetFuture)
//        );
//
//        channelFuture.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture future) throws Exception {
//                if(!future.isSuccess()){
//                    log.error("Exception during retrieval of virtual sensor status (Graph: {})!",
//                            dataOrigin.getGraphName(), future.getCause());
//                }
//            }
//        });
//
//        Futures.addCallback(resultSetFuture, new FutureCallback<ResultSet>() {
//            @Override
//            public void onSuccess(@Nullable ResultSet resultSet) {
//                if (resultSet == null || !resultSet.hasNext()) {
//                    GraphStatusErrorMessage graphStatus = new GraphStatusErrorMessage(
//                            HttpResponseStatus.NOT_FOUND,
//                            String.format("Graph %s not found!", dataOrigin.getGraphName())
//                    );
//
//                    graphStatusFuture.set(graphStatus);
//                    return;
//                }
//
//                Model vsModel = ModelFactory.createDefaultModel();
//
//                while (resultSet.hasNext()) {
//                    Binding binding = resultSet.nextBinding();
//
//                    Node subject = binding.get(Var.alloc("s"));
//                    Node predicate = binding.get(Var.alloc("p"));
//                    Node object = binding.get(Var.alloc("o"));
//
//                    Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
//                            ResourceFactory.createResource(subject.getURI());
//
//                    Property p = ResourceFactory.createProperty(predicate.getURI());
//
//                    RDFNode o = object.isBlank() ? ResourceFactory.createResource(object.getBlankNodeLabel()) :
//                            object.isLiteral() ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm()) :
//                                    ResourceFactory.createResource(object.getURI());
//
//                    vsModel.add(vsModel.createStatement(s, p, o));
//                }
//
//                graphStatusFuture.set(new ExpiringGraphHttpResponse(
//                        HttpResponseStatus.OK, new ExpiringNamedGraph(dataOrigin.getGraphName(), vsModel))
//                );
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                log.error("Failed to retrieve virtual sensor status (Graph: {})", dataOrigin.getGraphName(), t);
//                graphStatusFuture.setException(t);
//            }
//        });
//
//        return graphStatusFuture;
//    }

}
