package eu.spitfire.ssp.backends.slse;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.server.common.messages.*;
import eu.spitfire.ssp.server.common.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph;
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
                Model slseModel = ModelFactory.createDefaultModel();

                //Get the first row of the result set
                RDFNode sensorValue;

                if(resultSet.hasNext()){
                    QuerySolution querySolution = resultSet.nextSolution();
                    sensorValue = querySolution.get(querySolution.varNames().next());
                }

                else{
                    sensorValue = ResourceFactory.createTypedLiteral("0", XSDDatatype.XSDinteger);
                }

                Statement statement  = slseModel.createStatement(
                        slseModel.createResource(dataOrigin.getGraphName().toString()),
                        slseModel.createProperty("http://spitfire-project.eu/ontology/ns/value"),
                        sensorValue
                );

                slseModel.add(statement);

                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(dataOrigin.getGraphName(), slseModel,
                        new Date(System.currentTimeMillis() + DataOriginAccessor.MILLIS_PER_YEAR));

                ExpiringNamedGraphStatusMessage statusMessage = new ExpiringNamedGraphStatusMessage(expiringNamedGraph);

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
