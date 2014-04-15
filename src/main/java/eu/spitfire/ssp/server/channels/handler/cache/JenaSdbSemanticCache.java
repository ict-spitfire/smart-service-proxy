//package eu.spitfire.ssp.server.channels.handler.cache;
//
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
//import com.hp.hpl.jena.ontology.OntModel;
//import com.hp.hpl.jena.ontology.OntModelSpec;
//import com.hp.hpl.jena.query.*;
//import com.hp.hpl.jena.rdf.model.*;
//import com.hp.hpl.jena.reasoner.Reasoner;
//import com.hp.hpl.jena.reasoner.ReasonerRegistry;
//import com.hp.hpl.jena.sdb.SDB;
//import com.hp.hpl.jena.sdb.SDBFactory;
//import com.hp.hpl.jena.sdb.Store;
//import com.hp.hpl.jena.sdb.StoreDesc;
//import com.hp.hpl.jena.sdb.sql.JDBC;
//import com.hp.hpl.jena.sdb.sql.SDBConnection;
//import com.hp.hpl.jena.sdb.store.DatabaseType;
//import com.hp.hpl.jena.sdb.store.LayoutType;
//import com.hp.hpl.jena.sdb.util.StoreUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.net.*;
//import java.util.Date;
//import java.util.concurrent.ScheduledExecutorService;
//
///**
//* A semantic cache which is backed by a triple-store based on Jena SDB in a mySQL database. The database
//* is suitable to process SPARQL queries.
//*
//* Note: The database (scheme) given as part of the JDBC-URL in ssp.properties must already exist!
//*
//* @author Oliver Kleine
//*/
//public class JenaSdbSemanticCache extends SemanticCache {
//
//    private static final String SPT_SOURCE = "http://spitfire-project.eu/ontology.rdf";
//    private static final String SPT_NS = "http://spitfire-project.eu/ontology/ns/";
//    private static final String SPTSN_SOURCE = "http://spitfire-project.eu/sn.rdf";
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private StoreDesc storeDescription;
//    private SDBConnection sdbConnection;
//    private Reasoner reasoner;
//
//    public JenaSdbSemanticCache(ScheduledExecutorService executorService, String jdbcUrl, String user, String password)
//        throws Exception{
//
//        super(executorService);
//        Class.forName("com.mysql.jdbc.Driver");
//        JDBC.loadDriverMySQL();
//
//        storeDescription = new StoreDesc(LayoutType.LayoutTripleNodesHash, DatabaseType.MySQL);
//        sdbConnection = new SDBConnection(jdbcUrl, user, password);
//
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//
//        if(!StoreUtils.isFormatted(store))
//            store.getTableFormatter().create();
//
//        store.getTableFormatter().truncate();
//        log.info("JDBC Connection established. Database is ready.");
//
//        store.close();
//
//        OntModel ontologyBaseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
//        if (isUriAccessible(SPT_SOURCE))
//            ontologyBaseModel.read(SPT_SOURCE, "RDF/XML");
//
//        if (isUriAccessible(SPTSN_SOURCE))
//            ontologyBaseModel.read(SPTSN_SOURCE, "RDF/XML");
//
//        reasoner = ReasonerRegistry.getRDFSSimpleReasoner().bindSchema(ontologyBaseModel);
//    }
//
//    private static boolean isUriAccessible(String uri) {
//        HttpURLConnection connection = null;
//        int code = -1;
//        URL myurl;
//        try {
//            myurl = new URL(uri);
//
//            connection = (HttpURLConnection) myurl.openConnection();
//            connection.setRequestMethod("GET");
//            connection.setConnectTimeout(1000);
//            code = connection.getResponseCode();
//        }
//        catch (IOException e) {
//            System.err.println(uri + " is not accessible.");
//        }
//        return code == 200;
//    }
//
//    @Override
//    public InternalResourceStatusMessage getCachedResource(URI resourceUri) throws Exception{
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//        Model model = SDBFactory.connectNamedModel(store, resourceUri.toString());
//        store.close();
//        if(model.listSubjects().hasNext()){
//            log.info("Resource {} found in cache.", resourceUri);
//            return new InternalResourceStatusMessage(model, new Date());
//        }
//        else{
//            log.info("Resource {} NOT found in cache.", resourceUri);
//            return null;
//        }
//    }
//
//    @Override
//    public boolean containsNamedGraph(URI graphName) {
//        return false;
//    }
//
//    @Override
//    public synchronized void putNamedGraphToCache(URI graphName, Model namedGraph) {
//        deleteNamedGraph(graphName);
//
//        log.info("Put status of resource {} into cache.", graphName);
//
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//        Model sdbModel = SDBFactory.connectNamedModel(store, graphName.toString());
//
//        sdbModel.getLock().enterCriticalSection(true);
//        sdbModel.removeAll();
//        sdbModel.add(ModelFactory.createInfModel(reasoner, namedGraph));
//        sdbModel.add(namedGraph);
//
//        sdbModel.getLock().leaveCriticalSection();
//
//        store.close();
//    }
//
//    @Override
//    public synchronized void deleteNamedGraph(URI graphName) {
//        log.info("Delete status of resource {} from cache.", graphName);
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//        Model sdbModel = SDBFactory.connectNamedModel(store, graphName.toString());
//        sdbModel.getLock().enterCriticalSection(true);
//        sdbModel.removeAll();
//        sdbModel.getLock().leaveCriticalSection();
//
//        store.close();
//    }
//
//    @Override
//    public void updateStatement(Statement statement) {
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//        Model sdbModel = SDBFactory.connectNamedModel(store, statement.getSubject());
//
//        sdbModel.getLock().enterCriticalSection(true);
//        Statement oldStatement = sdbModel.getProperty(statement.getSubject(), statement.getPredicate());
//        Statement updatedStatement;
//        if(oldStatement != null){
//            if("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())){
//                RDFNode object =
//                        sdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
//                updatedStatement = oldStatement.changeObject(object);
//
//            }
//            else{
//                updatedStatement = oldStatement.changeObject(statement.getObject());
//            }
//            log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
//                    updatedStatement.getSubject(), updatedStatement.getObject()});
//        }
//        else
//            log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
//                    statement.getPredicate());
//
//        sdbModel.getLock().leaveCriticalSection();
//    }
//
//    public synchronized void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery){
//        log.info("Start SPAQRL query processing: {}", sparqlQuery);
//
//        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
//        Dataset dataset = SDBFactory.connectDataset(store);
//
//        Query query = QueryFactory.create(sparqlQuery);
//        QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset);
//        queryExecution.getContext().set(SDB.unionDefaultGraph, true);
//
//        try{
//            ResultSet resultSet = queryExecution.execSelect();
//
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            ResultSetFormatter.outputAsXML(baos, resultSet);
//            String result = baos.toString("UTF-8");
//
//            queryResultFuture.set(result);
//        }
//        catch (Exception e){
//            queryResultFuture.setException(e);
//        }
//        finally {
//            queryExecution.close();
//            store.close();
//        }
//    }
//
//    @Override
//    public boolean supportsSPARQL() {
//        return true;
//    }
//}
