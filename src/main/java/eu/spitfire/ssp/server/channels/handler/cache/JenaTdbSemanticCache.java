package eu.spitfire.ssp.server.channels.handler.cache;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

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

    public JenaTdbSemanticCache(ScheduledExecutorService scheduledExecutorService, Path dbDirectory){
        super(scheduledExecutorService);
        dataset = TDBFactory.createDataset(dbDirectory.toString());
        TDB.getContext().set(TDB.symUnionDefaultGraph, true) ;
    }

    @Override
    public InternalResourceStatusMessage getCachedResource(URI resourceUri) throws Exception{
        dataset.begin(ReadWrite.READ) ;
        try {
            Model model = dataset.getNamedModel(resourceUri.toString());

            if(model.isEmpty()){
                log.warn("No cached status found for resource {}", resourceUri);
                return null;
            }

            log.info("Cached status found for resource {}", resourceUri);
            return new InternalResourceStatusMessage(model, new Date());
        }
        finally{
            dataset.end();
        }
    }

    @Override
    public void putResourceToCache(URI resourceUri, Model resourceStatus) throws Exception{
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
    public void deleteResource(URI resourceUri) throws Exception{
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
    public void updateStatement(Statement statement) throws Exception{
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

    @Override
    public boolean supportsSPARQL() {
        return true;
    }
}
