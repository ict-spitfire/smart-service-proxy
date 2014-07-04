package eu.spitfire.ssp.backends.virtualsensors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.common.messages.ExpiringGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorAccessor extends Accessor<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private LocalServerChannel localChannel;
    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.Accessor}
     *
     * @param componentFactory
     */
    protected VirtualSensorAccessor(VirtualSensorBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.localChannel = componentFactory.getLocalChannel();
    }


    @Override
    public ListenableFuture<GraphStatusMessage> getStatus(final DataOrigin<URI> dataOrigin){

        log.info("Try to get status for data origin with identifier {}", dataOrigin.getIdentifier());

        final SettableFuture<GraphStatusMessage> graphStatusFuture = SettableFuture.create();

        Query sparqlQuery = QueryFactory.create(
                String.format("SELECT ?s ?p ?o FROM <%s> WHERE {?s ?p ?o}", dataOrigin.getGraphName())
        );

        SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();

        ChannelFuture channelFuture = Channels.write(
                this.localChannel, new SparqlQueryMessage(sparqlQuery, resultSetFuture)
        );

        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(!future.isSuccess()){
                    log.error("Exception during retrieval of virtual sensor status (Graph: {})!",
                            dataOrigin.getGraphName(), future.getCause());
                }
            }
        });

        Futures.addCallback(resultSetFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(@Nullable ResultSet resultSet) {
                if(resultSet == null){
                    GraphStatusErrorMessage graphStatus = new GraphStatusErrorMessage(
                            HttpResponseStatus.NOT_FOUND,
                            String.format("Graph %s not found!", dataOrigin.getGraphName())
                    );

                    graphStatusFuture.set(graphStatus);
                    return;
                }

                Model vsModel = ModelFactory.createDefaultModel();

                while(resultSet.hasNext()){
                    Binding binding = resultSet.nextBinding();

                    Node subject = binding.get(Var.alloc("s"));
                    Node predicate = binding.get(Var.alloc("p"));
                    Node object = binding.get(Var.alloc("o"));

                    Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
                            ResourceFactory.createResource(subject.getURI());

                    Property p =  ResourceFactory.createProperty(predicate.getURI());

                    RDFNode o = object.isBlank() ? ResourceFactory.createResource(object.getBlankNodeLabel()) :
                            object.isLiteral() ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm()) :
                                    ResourceFactory.createResource(object.getURI());

                    vsModel.add(vsModel.createStatement(s, p, o));
                }

                graphStatusFuture.set(new ExpiringGraphStatusMessage(
                        HttpResponseStatus.OK, new ExpiringNamedGraph(dataOrigin.getGraphName(), vsModel))
                );
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Failed to retrieve virtual sensor status (Graph: {})", dataOrigin.getGraphName(), t);
                graphStatusFuture.setException(t);
            }
        });

        return graphStatusFuture;
    }
}
