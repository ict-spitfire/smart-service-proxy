package eu.spitfire.ssp.backends.internal.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import eu.spitfire.ssp.backends.internal.se.SemanticEntity;
import eu.spitfire.ssp.server.internal.messages.requests.QueryStringTask;
import eu.spitfire.ssp.server.internal.messages.requests.QueryTask;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensor extends SemanticEntity{

    private static Logger log = LoggerFactory.getLogger(VirtualSensor.class.getName());

    private final Query sparqlQuery;
    private ScheduledFuture executionFuture;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public VirtualSensor(final URI identifier, final Query sparqlQuery, final Channel localChannel,
                         ScheduledExecutorService internalTasksExecutor){
        super(identifier);
        this.sparqlQuery = sparqlQuery;

        this.executionFuture = internalTasksExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
                QueryTask queryTask = new QueryTask(VirtualSensor.this.sparqlQuery, sparqlResultFuture);
                Channels.write(localChannel, queryTask);

                Futures.addCallback(sparqlResultFuture, new FutureCallback<ResultSet>() {
                    @Override
                    public void onSuccess(ResultSet resultSet) {

                        String sensorValue;

                        if (resultSet.hasNext()) {
                            QuerySolution querySolution = resultSet.nextSolution();
                            Literal tmp = querySolution.get("?aggVal").asLiteral();
                            sensorValue = "\"" + tmp.getValue().toString() + "\"^^<" + tmp.getDatatypeURI() + ">";
//                            sensorValue = ResourceFactory.createPlainLiteral(
//                                    querySolution.get("?aggVal").toString()
//                            );
                        } else {
                            sensorValue = ResourceFactory.createPlainLiteral("UNDEFINED").toString();
                        }

                        String fragment = identifier.getRawFragment();
                        String updateQuery = "PREFIX vs: <http://example.org/virtual-sensors#>\n" +
                                "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
                                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                                "\n" +
                                "DELETE {vs:" + fragment + "-SensorOutput ssn:hasValue ?o}\n" +
                                "INSERT {vs:" + fragment + "-SensorOutput ssn:hasValue " + sensorValue + "}\n" +
                                "WHERE  {vs:" + fragment + "-SensorOutput ssn:hasValue ?o}";


                        SettableFuture<ResultSet> updateFuture = SettableFuture.create();

                        Futures.addCallback(updateFuture, new FutureCallback<ResultSet>() {
                            @Override
                            public void onSuccess(ResultSet resultSet) {
                                log.info("Updated Virtual Sensor Value!");
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                log.error("Failed to update Virtual Sensor Value!", throwable);
                            }
                        });

                        QueryStringTask sensorValueUpdate = new QueryStringTask(updateQuery, updateFuture);

                        Channels.write(localChannel, sensorValueUpdate);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {

                    }
                });

            }
        }, 4, 4, TimeUnit.SECONDS);

    }

    public VirtualSensor(URI identifier, final Query sparqlQuery){
        this(identifier, sparqlQuery, null, null);
    }

    @Override
    public boolean isObservable() {
        return true;
    }

//    private SettableFuture<ResultSet> executeSparqlQuery(Query sparqlQuery){
//
//
//    }


    /**
     * Returns the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     * @return the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     */
    public Query getSparqlQuery() {
        return this.sparqlQuery;
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



}
