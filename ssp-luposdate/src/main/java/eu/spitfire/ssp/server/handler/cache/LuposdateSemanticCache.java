package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.*;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
import eu.spitfire.ssp.server.internal.messages.responses.QueryResult;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.BasicIndexQueryEvaluator;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.evaluators.QueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by olli on 17.06.14.
 */
public class LuposdateSemanticCache extends SemanticCache {

    private static Logger log = LoggerFactory.getLogger(LuposdateSemanticCache.class.getName());

    private BasicIndexQueryEvaluator evaluator;
    private AtomicInteger waitingOperations = new AtomicInteger(0);
    private AtomicInteger finishedOperations = new AtomicInteger(0);

    private ExecutorService cacheExecutor;

    public LuposdateSemanticCache(ExecutorService ioExecutorService,
                                  ScheduledExecutorService internalTasksExecutorService) {

        super(ioExecutorService, internalTasksExecutorService);
        this.initialize();

        this.cacheExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(
                "SSP Cache Thread #%d"
        ).build());
    }

    private void initialize(){
        try{
            MemoryIndexQueryEvaluator queryEvaluator = new MemoryIndexQueryEvaluator();
            queryEvaluator.setupArguments();
            queryEvaluator.getArgs().set("result",          lupos.datastructures.queryresult.QueryResult.TYPE.MEMORY);
            queryEvaluator.getArgs().set("codemap",         LiteralFactory.MapType.TRIEMAP);
            queryEvaluator.getArgs().set("distinct",        CommonCoreQueryEvaluator.DISTINCT.HASHSET);
            queryEvaluator.getArgs().set("join",            CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
            queryEvaluator.getArgs().set("optional",        CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
            queryEvaluator.getArgs().set("datastructure",   Indices.DATA_STRUCT.HASHMAP);

            this.evaluator = queryEvaluator;
            this.evaluator.init();

            Collection<URILiteral> uriLiterals = new LinkedList<>();
            uriLiterals.add(LiteralFactory.createStringURILiteral("<inlinedata:>"));
            queryEvaluator.prepareInputData(uriLiterals, new LinkedList<URILiteral>());

            //queryEvaluator.getDataset().addNamedGraph(LiteralFactory.createStringURILiteral("<nameofgraph>"), LiteralFactory.createStringURILiteral("<inlinedata:<a> <b> <c>>"), false, false);
            GeoFunctionRegisterer.registerGeoFunctions();
        }
        catch (Exception ex){
            log.error("This should never happen!", ex);
        }
    }


    @Override
    public ListenableFuture<Boolean> containsNamedGraph(final URI graphName) {
        final SettableFuture<Boolean> containsFuture = SettableFuture.create();
        containsFuture.set(false);
        return containsFuture;
    }


    @Override
    public ListenableFuture<ExpiringGraph> getNamedGraph(final URI graphName) {
        final SettableFuture<ExpiringGraph> result = SettableFuture.create();
        result.set(null);
        return result;
    }


    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, final Model namedGraph) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.cacheExecutor.execute(new DatabaseTask(){

            @Override
            public void process() {
                try{
                    putNamedGraphToCache2(graphName, namedGraph);
                    result.set(null);
                }
                catch (Exception ex){
                    log.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;

    }

    @Override
    public ListenableFuture<Void> updateSensorValue(final URI graphName, final RDFNode sensorValue) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.cacheExecutor.execute(new DatabaseTask(){

            @Override
            public void process() {
                try{
                    updateSensorValue2(graphName, sensorValue);
                    result.set(null);
                }
                catch (Exception ex){
                    log.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }


    @Override
    public ListenableFuture<QueryResult> processSparqlQuery(final String query) {
        final SettableFuture<QueryResult> resultFuture = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.cacheExecutor.execute(new DatabaseTask() {
            @Override
            public void process() {
                try{
                    log.debug("Start SPARQL query: \n{}", query);
                    long startTime = System.currentTimeMillis();
                    QueryResult queryResult = processSparqlQuery2(query);
                    resultFuture.set(queryResult);
                    log.debug("Query execution finished (duration: {} millis)", System.currentTimeMillis() - startTime);
                }
                catch (Exception e) {
                    log.error("Exception while processing SPARQL query.", e);
                    resultFuture.setException(e);
                }
                finally {
                    log.debug("Finished SPARQL query. \n{}");
                }
            }
        });

        return resultFuture;
    }


    @Override
    public ListenableFuture<Void> deleteNamedGraph(final URI graphName) {
        final SettableFuture<Void> result = SettableFuture.create();
        result.set(null);
        return result;
    }


    private void updateSensorValue2(URI graphName, RDFNode sensorValue) throws Exception{
        String updateQuery = "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
                "DELETE {<" + graphName + "-SensorOutput> ssn:hasValue ?value }\n" +
                "INSERT {<" + graphName + "-SensorOutput> ssn:hasValue " + sensorValue.toString() + " }";

        this.evaluator.getResult(updateQuery);
    }


    private void putNamedGraphToCache2(URI graphName, Model namedGraph) throws Exception{
        long startTime = System.currentTimeMillis();
        log.debug("Start insertion of graph {}", graphName);

        String query = createInsertOrDeleteQuery(true, namedGraph);
        this.evaluator.getResult(query);

        log.debug("Finished insertion of graph {} (duration: {} millis.",
                graphName, System.currentTimeMillis() - startTime);
    }


    private String createInsertOrDeleteQuery(boolean insert, URI graphName, Model namedGraph){
        StmtIterator stmtIterator = namedGraph.listStatements();
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append(insert ? "INSERT " : "DELETE ").append("DATA { ");
        if(graphName != null){
            queryBuilder.append("GRAPH <").append(graphName).append("> {");
        }

        while(stmtIterator.hasNext()){
            Statement statement = stmtIterator.nextStatement();
            queryBuilder.append("\t<").append(statement.getSubject().toString()).append("> <")
                    .append(statement.getPredicate().toString()).append("> ");

            if(statement.getObject().isLiteral()){
                if(statement.getObject().toString().contains("^^")){
                    String[] parts = statement.getObject().toString().split("\\^\\^");
                    queryBuilder.append("\"").append(parts[0]).append("\"^^<").append(parts[1]).append("> .\n");
                }
                else{
                    queryBuilder.append("\"").append(statement.getObject().toString()).append("\" .\n");
                }
            }

            else{
                queryBuilder.append("<").append(statement.getObject().toString()).append("> .\n");
            }
        }

        if(graphName != null){
            queryBuilder.append("}");
        }

        queryBuilder.append("}");

        return queryBuilder.toString();
    }


    private String createInsertOrDeleteQuery(boolean insert, Model namedGraph){
        return createInsertOrDeleteQuery(insert, null, namedGraph);
    }


    private QueryResult processSparqlQuery2(String sparqlQuery) throws Exception{
        final SettableFuture<QueryResult> queryResultFuture = SettableFuture.create();

        //Execute Query and make the result a JENA result set
        ListenableFuture<ResultSet> resultSetFuture = toResultSet(this.evaluator.getResult(sparqlQuery));
        Futures.addCallback(resultSetFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(@Nullable ResultSet resultSet) {
                try{
                    QueryResult queryResult = new QueryResult(resultSet);
                    queryResultFuture.set(queryResult);
                }
                catch(Exception ex){
                    log.error("Exception while creating internal SPARQL query result message.", ex);
                    queryResultFuture.setException(ex);
                }

            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Unexpected Exception!", t);
                queryResultFuture.setException(t);
            }
        });

        return queryResultFuture.get();
    }


//    private Model toModel(ResultSet resultSet){
//        Model model = ModelFactory.createDefaultModel();
//
//        if(resultSet == null){
//            log.error("ResultSet was NULL!!!");
//            return null;
//        }
//
//
//        while(resultSet.hasNext()){
//            Binding binding = resultSet.nextBinding();
//
//            com.hp.hpl.jena.graph.Node subject = binding.get(Var.alloc("s"));
//            com.hp.hpl.jena.graph.Node predicate = binding.get(Var.alloc("p"));
//            com.hp.hpl.jena.graph.Node object = binding.get(Var.alloc("o"));
//
//            Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
//                    ResourceFactory.createResource(subject.getURI());
//
//            Property p =  ResourceFactory.createProperty(predicate.getURI());
//
//            RDFNode o = object.isBlank() ? ResourceFactory.createResource(object.getBlankNodeLabel()) :
//                    object.isLiteral() ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm()) :
//                            ResourceFactory.createResource(object.getURI());
//
//            model.add(model.createStatement(s, p, o));
//
//        }
//
//        return model;
//    }


    private ListenableFuture<ResultSet> toResultSet(final lupos.datastructures.queryresult.QueryResult queryResult) throws IOException {
        long start = System.currentTimeMillis();
        final SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();
        final File tmpFile = File.createTempFile("queryResult", ".xml");

        XMLFormatter formatter = new XMLFormatter();
        formatter.writeResult(new FileOutputStream(tmpFile), queryResult.getVariableSet(), queryResult);

        ResultSet result = ResultSetFactory.fromXML(new FileInputStream(tmpFile));
        resultSetFuture.set(result);

        if(!tmpFile.delete()){
            log.error("Temporary file {} could not deleted...", tmpFile.getAbsolutePath());
        }

        log.debug("Time to re-format result-set: {} millis", System.currentTimeMillis() - start);
        return resultSetFuture;
    }

    
    private abstract class DatabaseTask implements Runnable{
        
        @Override
        public void run(){
            int stillWaiting = waitingOperations.decrementAndGet();
            log.debug("Start DB operation (still waiting: {})", stillWaiting);

            process();

            int finished = finishedOperations.incrementAndGet();
            if(log.isInfoEnabled() && (finished % 100 == 0 || stillWaiting == 0)){
                log.info("Finished DB operation #{} (still waiting: {}) ", finished, stillWaiting);
            }
        }
        
        public abstract void process();
    }

}
