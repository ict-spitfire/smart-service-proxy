package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sdb.SDB;
import com.hp.hpl.jena.sdb.SDBFactory;
import com.hp.hpl.jena.sdb.Store;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 05.09.13
 * Time: 09:43
 * To change this template use File | Settings | File Templates.
 */
public class JenaTdbSemanticCache extends AbstractSemanticCache{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Dataset dataset;

    public JenaTdbSemanticCache(Path dbDirectory){
        dataset = TDBFactory.createDataset(dbDirectory.toString());
        TDB.getContext().set(TDB.symUnionDefaultGraph, true) ;
    }

    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        dataset.begin(ReadWrite.READ) ;
        try {
            Model model = dataset.getNamedModel(resourceUri.toString());

            if(model.listStatements().hasNext()){
                log.info("Status found for resource {}", resourceUri);
                return new ResourceStatusMessage(resourceUri, model, new Date());
            }
            else{
                log.info("No status found for resource {}", resourceUri);
                return null;
            }
        }
        finally{
            dataset.end();
        }
    }

    @Override
    public void putResourceToCache(URI resourceUri, Model resourceStatus, Date expiry) {
        deleteResource(resourceUri);

        dataset.begin(ReadWrite.WRITE);
        try{
            dataset.addNamedModel(resourceUri.toString(), resourceStatus);
            dataset.commit();
            log.info("Added status for resource {}", resourceUri);
        }
        finally {
            dataset.end();
        }
    }

    @Override
    public void deleteResource(URI resourceUri) {
        dataset.begin(ReadWrite.WRITE);
        try{
            dataset.removeNamedModel(resourceUri.toString());
            dataset.commit();
            log.info("Removed status for resource {}", resourceUri);
        }
        finally {
            dataset.end();
        }
    }

    @Override
    public void updateStatement(Statement statement) {
        dataset.begin(ReadWrite.WRITE);
        try{
            Model tdbModel = dataset.getNamedModel(statement.getSubject().toString());
            Statement oldStatement = tdbModel.getProperty(statement.getSubject(), statement.getPredicate());
            Statement updatedStatement;
            if(oldStatement != null){
                if("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())){
                    RDFNode object =
                            tdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
                    updatedStatement = oldStatement.changeObject(object);
                    dataset.commit();

                }
                else{
                    updatedStatement = oldStatement.changeObject(statement.getObject());
                    dataset.commit();
                }
                log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
                        updatedStatement.getSubject(), updatedStatement.getObject()});
            }
            else
                log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
                        statement.getPredicate());
        }
        finally {
            dataset.end();
        }
    }

    public synchronized void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery){

        dataset.begin(ReadWrite.READ);
        try{
            log.info("Start SPAQRL query processing: {}", sparqlQuery);

            Query query = QueryFactory.create(sparqlQuery);
            QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try{
                ResultSet resultSet = queryExecution.execSelect();
                ResultSetFormatter.outputAsXML(baos, resultSet);
            }
            finally {
                queryExecution.close();
            }
            String result = baos.toString("UTF-8");

            queryResultFuture.set(result);
        }
        catch (Exception e){
            queryResultFuture.setException(e);
        }
        finally {
            dataset.end();
        }
    }
}
