package eu.spitfire.ssp.backends.slse;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.server.common.messages.ExpiringGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.common.wrapper.ExpiringGraph;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 06.05.14.
 */
public class SlseAccessor extends DataOriginAccessor<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor}
     *
     * @param componentFactory
     */
    protected SlseAccessor(BackendComponentFactory<URI> componentFactory) {
        super(componentFactory);
    }

    @Override
    public ListenableFuture<GraphStatusMessage> getStatus(final DataOrigin<URI> dataOrigin)
            throws DataOriginAccessException{

        log.info("Try to get status for data origin with identifier {}", dataOrigin.getIdentifier());

        final SettableFuture<GraphStatusMessage> result = SettableFuture.create();

        Query sparqlQuery = ((SlseDataOrigin) dataOrigin).getSparqlQuery();
        SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();

        Channels.write(this.componentFactory.getLocalChannel(), new SparqlQueryMessage(sparqlQuery, queryResultFuture));

        Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                if(!resultSet.hasNext()){
                    result.setException(new DataOriginAccessException(HttpResponseStatus.NOT_FOUND,
                            "No sensors found to aggregate!"));
                    return;
                }

                QuerySolution querySolution = resultSet.next();

                Model slseModel = ModelFactory.createDefaultModel();
                Statement statement = slseModel.createStatement(
                        slseModel.createResource(dataOrigin.getGraphName().toString()),
                        slseModel.createProperty("http://spitfire-project.eu/ontology/ns/value"),
                        querySolution.get(querySolution.varNames().next())
                );

                slseModel.add(statement);

                ExpiringGraph expiringGraph = new ExpiringGraph(slseModel, new Date());
                ExpiringGraphStatusMessage statusMessage = new ExpiringGraphStatusMessage(HttpResponseStatus.OK,
                        expiringGraph);

                result.set(statusMessage);

            }

            @Override
            public void onFailure(Throwable t) {
                result.setException(t);
            }

        }, this.componentFactory.getInternalTasksExecutorService());

        return result;
    }

    @Override
    public ListenableFuture<GraphStatusMessage> setStatus(URI identifier, Model status)
            throws DataOriginAccessException {

        SettableFuture<GraphStatusMessage> result = SettableFuture.create();
        GraphStatusErrorMessage message = new GraphStatusErrorMessage(HttpResponseStatus.METHOD_NOT_ALLOWED,
                "Only GET requests are allowed!");

        result.set(message);

        return result;
    }

    @Override
    public ListenableFuture<GraphStatusMessage> deleteDataOrigin(URI identifier) throws DataOriginAccessException {
        return null;
    }
}
