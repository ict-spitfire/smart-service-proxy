package eu.spitfire.ssp.server.channels.handler.cache;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

import com.hp.hpl.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;

import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 05.09.13
 * Time: 09:43
 * To change this template use File | Settings | File Templates.
 */
public class JenaTdbSemanticCache extends SemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Dataset dataset;

    private static final String SPT_SOURCE = "http://spitfire-project.eu/ontology.rdf";
    private static final String SPTSN_SOURCE = "http://spitfire-project.eu/sn.rdf";


    private static OntModel ontologyBaseModel = null;


    public JenaTdbSemanticCache(ScheduledExecutorService scheduledExecutorService, Path dbDirectory) {
        super(scheduledExecutorService);
        dataset = TDBFactory.createDataset(dbDirectory.toString());
        TDB.getContext().set(TDB.symUnionDefaultGraph, true);

        if (ontologyBaseModel == null) {
            ontologyBaseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
            ontologyBaseModel.read(SPT_SOURCE, "RDF/XML");
            ontologyBaseModel.read(SPTSN_SOURCE, "RDF/XML");
        }
    }

    @Override
    public InternalResourceStatusMessage getCachedResource(URI resourceUri) throws Exception {
        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getNamedModel(resourceUri.toString());

            if (model.isEmpty()) {
                log.warn("No cached status found for resource {}", resourceUri);
                return null;
            }
            log.info("Cached status found for resource {}", resourceUri);
            return new InternalResourceStatusMessage(model, new Date());
        } finally

        {
            dataset.end();
        }

    }

    @Override
    public void putResourceToCache(URI resourceUri, Model resourceStatus) throws Exception {
        deleteResource(resourceUri);

        dataset.begin(ReadWrite.WRITE);
        try {
            Model owlFullModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
            owlFullModel.add(resourceStatus);
//            dataset.addNamedModel(resourceUri.toString(), resourceStatus);
            dataset.addNamedModel(resourceUri.toString(), owlFullModel);
            dataset.commit();
            log.debug("Added status for resource {}", resourceUri);
        } finally {
            dataset.end();
        }
    }

    @Override
    public void deleteResource(URI resourceUri) throws Exception {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.removeNamedModel(resourceUri.toString());
            dataset.commit();
            log.debug("Removed status for resource {}", resourceUri);
        } finally {
            dataset.end();
        }
    }

    @Override
    public void updateStatement(Statement statement) throws Exception {
        dataset.begin(ReadWrite.WRITE);
        try {
            Model tdbModel = dataset.getNamedModel(statement.getSubject().toString());
//            Model model = dataset.getNamedModel(statement.getSubject().toString());
//            Model tdbModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
//            tdbModel.add(model);

            Statement oldStatement = tdbModel.getProperty(statement.getSubject(), statement.getPredicate());
            Statement updatedStatement;
            if (oldStatement != null) {
                if ("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())) {
                    RDFNode object =
                            tdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
                    updatedStatement = oldStatement.changeObject(object);
                    dataset.commit();

                } else {
                    updatedStatement = oldStatement.changeObject(statement.getObject());
                    dataset.commit();
                }
                log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
                        updatedStatement.getSubject(), updatedStatement.getObject()});
            } else
                log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
                        statement.getPredicate());
        } finally {
            dataset.end();
        }
    }

    public synchronized void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery) {

        dataset.begin(ReadWrite.READ);
        Model model = dataset.getNamedModel("DEFAULT");
        model.add(ontologyBaseModel);
        try {
            log.info("Start SPARQL query processing: {}", sparqlQuery);

            Query query = QueryFactory.create(sparqlQuery);
            QueryExecution queryExecution = QueryExecutionFactory.create(query, model);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                ResultSet resultSet = queryExecution.execSelect();
                ResultSetFormatter.outputAsXML(baos, resultSet);
            } finally {
                queryExecution.close();
            }
            String result = baos.toString("UTF-8");

            queryResultFuture.set(result);
        } catch (Exception e) {
            queryResultFuture.setException(e);
        } finally {
            dataset.end();
        }
    }

    @Override
    public boolean supportsSPARQL() {
        return true;
    }
}
