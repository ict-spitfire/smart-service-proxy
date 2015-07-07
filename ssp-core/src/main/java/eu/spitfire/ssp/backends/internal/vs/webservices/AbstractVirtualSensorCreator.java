package eu.spitfire.ssp.backends.internal.vs.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorRegistry;
import eu.spitfire.ssp.server.internal.messages.requests.InternalQueryRequest;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.Language;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Created by olli on 30.06.14.
 */
public abstract class AbstractVirtualSensorCreator extends HttpWebservice{

    private static Logger log = LoggerFactory.getLogger(AbstractVirtualSensorCreator.class.getName());

    private VirtualSensorRegistry virtualSensorRegistry;
    private LocalServerChannel localChannel;
    private String graphNamePrefix;


    protected AbstractVirtualSensorCreator(VirtualSensorBackendComponentFactory componentFactory,
                                           String graphNamePrefix, String htmlResourcePath){

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);
        this.virtualSensorRegistry = componentFactory.getRegistry();
        this.localChannel = componentFactory.getLocalChannel();
        this.graphNamePrefix = graphNamePrefix;
    }


    /**
     * Returns the fully qualified name of a graph with the given path
     *
     * @param uriPath the path of the graph name
     *
     * @return the fully qualified name of a graph with the given path
     *
     * @throws URISyntaxException
     */
    protected URI createGraphName(String uriPath) throws URISyntaxException {
        return new URI(graphNamePrefix + uriPath);
    }

    protected Model createModel(String n3Ontology){
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(n3Ontology.getBytes(Charset.forName("UTF8"))), null, Language.RDF_N3.lang);
        return model;
    }

    protected ListenableFuture<Model> addSensorValueToModel(final String sensorName, final Model model, final Query query){
        final SettableFuture<Model> modelFuture = SettableFuture.create();

        Futures.addCallback(executeSparqlQuery(query), new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                try {

                    RDFNode sensorValue;

                    if (resultSet.hasNext()) {
                        QuerySolution querySolution = resultSet.nextSolution();
                        sensorValue = ResourceFactory.createPlainLiteral(
                                querySolution.get(querySolution.varNames().next()).toString()
                        );
                    } else {
                        sensorValue = ResourceFactory.createPlainLiteral("UNDEFINED");
                    }

                    Resource subject = model.getResource(
                            "http://example.org/virtual-sensors#" + sensorName + "-SensorOutput"
                    );

                    Statement statement = subject.getProperty(
                            model.getProperty("http://purl.oclc.org/NET/ssnx/ssn#", "hasValue")
                    );

                    statement.changeObject(sensorValue);

                    modelFuture.set(model);

                } catch (Exception ex) {
                    modelFuture.setException(ex);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("Exception while executing SPARQL query: {}", query, t);
                modelFuture.setException(t);
            }
        });

        return modelFuture;
    }

    private SettableFuture<ResultSet> executeSparqlQuery(Query sparqlQuery){

        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
        InternalQueryRequest internalQueryRequest = new InternalQueryRequest(sparqlQuery, sparqlResultFuture);
        Channels.write(this.localChannel, internalQueryRequest);

        return sparqlResultFuture;
    }

    protected VirtualSensorRegistry getVirtualSensorRegistry(){
        return this.virtualSensorRegistry;
    }
}
