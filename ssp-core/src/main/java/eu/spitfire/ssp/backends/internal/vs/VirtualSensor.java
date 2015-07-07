package eu.spitfire.ssp.backends.internal.vs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;
import eu.spitfire.ssp.backends.internal.se.SemanticEntity;
import eu.spitfire.ssp.server.internal.messages.requests.InternalUpdateRequest;
import eu.spitfire.ssp.server.internal.messages.requests.InternalQueryRequest;
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

    private VirtualSensorUpdateTask updateTask;
    private ScheduledFuture executionFuture;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public VirtualSensor(final URI identifier, final Query query, final Channel localChannel,
                         ScheduledExecutorService internalTasksExecutor){
        super(identifier);

        this.updateTask = new VirtualSensorUpdateTask(query, localChannel);
        this.executionFuture = internalTasksExecutor.scheduleAtFixedRate(this.updateTask,  4, 4, TimeUnit.SECONDS);
    }

    public VirtualSensor(URI identifier, final Query sparqlQuery){
        this(identifier, sparqlQuery, null, null);
    }


    @Override
    public boolean isObservable() {
        return true;
    }


    /**
     * Returns the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     * @return the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     */
    public Query getQuery() {
        return this.updateTask.getQuery();
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


    private class VirtualSensorUpdateTask implements Runnable{

        private Query query;
        private Channel localChannel;

        private VirtualSensorUpdateTask(Query query, Channel localChannel){
            this.query = query;
            this.localChannel = localChannel;
        }

        private Query getQuery(){
            return this.query;
        }

        @Override
        public void run() {
            SettableFuture<ResultSet> queryResultFuture = SettableFuture.create();
            InternalQueryRequest internalQueryRequest = new InternalQueryRequest(query, queryResultFuture);
            Channels.write(localChannel, internalQueryRequest);

            //Await the result of the query execution
            Futures.addCallback(queryResultFuture, new FutureCallback<ResultSet>() {
                @Override
                public void onSuccess(ResultSet resultSet) {

                    String sensorValue;

                    if (resultSet.hasNext()) {
                        QuerySolution querySolution = resultSet.nextSolution();
                        Literal tmp = querySolution.get("?aggVal").asLiteral();
                        sensorValue = "\"" + tmp.getValue().toString() + "\"^^<" + tmp.getDatatypeURI() + ">";
                    }
                    else {
                        sensorValue = ResourceFactory.createPlainLiteral("UNDEFINED").toString();
                    }

                    //Update sensor value in cache
                    String fragment = getIdentifier().getRawFragment();
                    String updateQuery = "PREFIX vs: <http://example.org/virtual-sensors#>\n" +
                            "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
                            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                            "\n" +
                            "DELETE {vs:" + fragment + "-SensorOutput ssn:hasValue ?o}\n" +
                            "INSERT {vs:" + fragment + "-SensorOutput ssn:hasValue " + sensorValue + "}\n" +
                            "WHERE  {vs:" + fragment + "-SensorOutput ssn:hasValue ?o}";


                    UpdateRequest updateRequest = UpdateFactory.create(updateQuery);
                    InternalUpdateRequest internalUpdateRequest = new InternalUpdateRequest(updateRequest);

                    Futures.addCallback(internalUpdateRequest.getUpdateFuture(), new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void v) {
                            log.info("Updated Virtual Sensor Value!");
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.error("Failed to update Virtual Sensor Value!", throwable);
                        }
                    });

                    Channels.write(localChannel, internalUpdateRequest);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.error("Failed to retrieve Virtual Sensor Value!", throwable);
                }
            });

        }
    }


}
