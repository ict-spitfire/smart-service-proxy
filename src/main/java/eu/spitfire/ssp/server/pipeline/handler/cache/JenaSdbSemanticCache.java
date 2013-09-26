package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sdb.SDB;
import com.hp.hpl.jena.sdb.SDBFactory;
import com.hp.hpl.jena.sdb.Store;
import com.hp.hpl.jena.sdb.StoreDesc;
import com.hp.hpl.jena.sdb.sql.JDBC;
import com.hp.hpl.jena.sdb.sql.SDBConnection;
import com.hp.hpl.jena.sdb.store.DatabaseType;
import com.hp.hpl.jena.sdb.store.LayoutType;
import com.hp.hpl.jena.sdb.util.StoreUtils;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Date;

/**
 * A semantic cache which is backed by a triple-store based on Jena SDB in a mySQL database. The database
 * is suitable to process SPARQL queries.
 *
 * Note: The database (scheme) given as part of the JDBC-URL in ssp.properties must already exist!
 *
 * @author Oliver Kleine
 */
public class JenaSdbSemanticCache extends SemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private StoreDesc storeDescription;
    private SDBConnection sdbConnection;

    public JenaSdbSemanticCache(String jdbcUrl, String user, String password) throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        JDBC.loadDriverMySQL();

        storeDescription = new StoreDesc(LayoutType.LayoutTripleNodesHash, DatabaseType.MySQL);
        sdbConnection = new SDBConnection(jdbcUrl, user, password);

        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);

        if(!StoreUtils.isFormatted(store))
            store.getTableFormatter().create();

        store.getTableFormatter().truncate();
        log.info("JDBC Connection established. Database is ready.");

        store.close();
    }

    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model model = SDBFactory.connectNamedModel(store, resourceUri.toString());

        store.close();
        if(model.listSubjects().hasNext()){
            log.info("Resource {} found in cache.", resourceUri);
            return new ResourceStatusMessage(HttpResponseStatus.OK, model.getResource(resourceUri.toString()),
                    new Date());
        }
        else{
            log.info("Resource {} NOT found in cache.", resourceUri);
            return null;
        }
    }

    @Override
    public synchronized void putResourceToCache(URI resourceUri, Model resourceStatus, Date expiry) {
        deleteResource(resourceUri);

        log.info("Put status of resource {} into cache.", resourceUri);

        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model sdbModel = SDBFactory.connectNamedModel(store, resourceUri.toString());

        sdbModel.getLock().enterCriticalSection(true);
        sdbModel.add(resourceStatus);
        sdbModel.getLock().leaveCriticalSection();

        store.close();
    }

    @Override
    public synchronized void deleteResource(URI resourceUri) {
        log.info("Delete status of resource {} from cache.", resourceUri);
        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model sdbModel = SDBFactory.connectNamedModel(store, resourceUri.toString());
        sdbModel.getLock().enterCriticalSection(true);
        sdbModel.removeAll();
        sdbModel.getLock().leaveCriticalSection();

        store.close();
    }

    @Override
    public void updateStatement(Statement statement) {
        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model sdbModel = SDBFactory.connectNamedModel(store, statement.getSubject());

        sdbModel.getLock().enterCriticalSection(true);
        Statement oldStatement = sdbModel.getProperty(statement.getSubject(), statement.getPredicate());
        Statement updatedStatement;
        if(oldStatement != null){
            if("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())){
                RDFNode object =
                        sdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
                updatedStatement = oldStatement.changeObject(object);

            }
            else{
                updatedStatement = oldStatement.changeObject(statement.getObject());
            }
            log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
                    updatedStatement.getSubject(), updatedStatement.getObject()});
        }
        else
            log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
                    statement.getPredicate());

        sdbModel.getLock().leaveCriticalSection();
    }

    public synchronized void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery){
        log.info("Start SPAQRL query processing: {}", sparqlQuery);

        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Dataset dataset = SDBFactory.connectDataset(store);

        Query query = QueryFactory.create(sparqlQuery);
        QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset);
        queryExecution.getContext().set(SDB.unionDefaultGraph, true);

        try{
            ResultSet resultSet = queryExecution.execSelect();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ResultSetFormatter.outputAsXML(baos, resultSet);
            String result = baos.toString("UTF-8");

            queryResultFuture.set(result);
        }
        catch (Exception e){
            queryResultFuture.setException(e);
        }
        finally {
            queryExecution.close();
            store.close();
        }
    }

    @Override
    public boolean supportsSPARQL() {
        return true;
    }
}
