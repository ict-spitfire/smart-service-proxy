package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import eu.spitfire.ssp.server.internal.utils.Converter;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.BasicIndexQueryEvaluator;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import org.apache.jena.query.Query;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by olli on 17.06.14.
 */
public class LuposdateSemanticCache extends SemanticCache {

    private static Logger LOG = LoggerFactory.getLogger(LuposdateSemanticCache.class.getName());

    private BasicIndexQueryEvaluator evaluator;
    private AtomicInteger waitingOperations = new AtomicInteger(0);
    private AtomicInteger finishedOperations = new AtomicInteger(0);

    private ReentrantLock lock;

//    private ExecutorService cacheExecutor;

    public LuposdateSemanticCache(ExecutorService ioExecutorService,
                                  ScheduledExecutorService internalTasksExecutorService) {

        super(ioExecutorService, internalTasksExecutorService);
        this.initialize();
        this.lock = new ReentrantLock();
//        this.cacheExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(
//                "SSP Cache Thread #%d"
//        ).build());
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
            queryEvaluator.prepareInputData(uriLiterals, new LinkedList<>());

            //queryEvaluator.getDataset().addNamedGraph(LiteralFactory.createStringURILiteral("<nameofgraph>"), LiteralFactory.createStringURILiteral("<inlinedata:<a> <b> <c>>"), false, false);
            GeoFunctionRegisterer.registerGeoFunctions();
        }
        catch (Exception ex){
            LOG.error("This should never happen!", ex);
        }
    }


    @Override
    public ListenableFuture<Boolean> containsNamedGraph(final URI graphName) {
        final SettableFuture<Boolean> containsFuture = SettableFuture.create();
        containsFuture.set(false);
        return containsFuture;
    }


    @Override
    public ListenableFuture<ExpiringNamedGraph> getNamedGraph(final URI graphName) {
        final SettableFuture<ExpiringNamedGraph> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    result.set(getNamedGraph2(graphName));
                } catch (Exception ex) {
                    LOG.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }


    private ExpiringNamedGraph getNamedGraph2(URI graphName) throws Exception{
        String query = "SELECT ?s ?p ?o  WHERE {GRAPH <" + graphName + "> { ?s ?p ?o }}";

        QueryResult result = this.evaluator.getResult(query);
        Model model = Converter.toModel(toResultSet(result));

        return new ExpiringNamedGraph(graphName, model, new Date());
    }



    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, final Model namedGraph) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    putNamedGraphToCache2(graphName, namedGraph);
                    result.set(null);
                } catch (Exception ex) {
                    LOG.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }


    private void putNamedGraphToCache2(URI graphName, Model graph) throws Exception{
        long start = System.currentTimeMillis();

        //delete old triples
        this.evaluator.getResult(createDeleteQuery(graphName));
        LOG.info("Deleted graph \"{}\" (duration: {} ms)", graphName, System.currentTimeMillis() - start);

        //insert new triples from given graph
        this.evaluator.getResult(createInsertQuery(graphName, graph));
        LOG.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
    }

    private static final String DELETE_QUERY_TEMPLATE =
            "DELETE {\n\t" +
                "GRAPH <%s> { ?s ?p ?o }\n\t" +
                "?s ?p ?o\n" +
            "} WHERE {\n\t" +
                "GRAPH <%s> {?s ?p ?o }\n" +
            "}";

    private static String createDeleteQuery(URI graphName){
        String query = String.format(Locale.ENGLISH, DELETE_QUERY_TEMPLATE, graphName, graphName);
        LOG.debug("\n" + query);
        return query;
    }


    private static final String INSERT_QUERY_TEMPLATE =
            "INSERT DATA {\n\t" +
                "GRAPH <%s> {" +
                    "%s\n" +
                "}" +
                "%s\n" +
            "}";

    private static String createInsertQuery(URI graphName, Set<String> statements){
        String triples = "";
        for(String triple : statements){
            triples += triple;
        }

        String query = String.format(Locale.ENGLISH, INSERT_QUERY_TEMPLATE, graphName.toString(), "\t" + triples, triples);
        LOG.debug("\n" + query);
        return query;
    }

    private static final String TRIPLE_TEMPLATE_WITH_RESOURCE =
            "\n\t<%s> <%s> <%s> .";

    private static final String TRIPLE_TEMPLATE_WITH_UNTYPED_LITERAL =
            "\n\t<%s> <%s> \"%s\" .";

    private static final String TRIPLE_TEMPLATE_WITH_TYPED_LITERAL =
            "\n\t<%s> <%s> \"%s\"^^<%s> .";

    private static String createTriple(Statement statement){

        String subject = statement.getSubject().toString();
        String predicate = statement.getPredicate().toString();
        RDFNode object = statement.getObject();

        if(object.isLiteral()){
            //typed literal
            if(object.toString().contains("^^")){
                String[] parts = object.toString().split("\\^\\^");
                return String.format(
                    Locale.ENGLISH, TRIPLE_TEMPLATE_WITH_TYPED_LITERAL, subject, predicate, parts[0], parts[1]
                );
            }
            //untyped literal
            return String.format(
                Locale.ENGLISH, TRIPLE_TEMPLATE_WITH_UNTYPED_LITERAL, subject, predicate, object.toString()
            );
        }

        return String.format(Locale.ENGLISH, TRIPLE_TEMPLATE_WITH_RESOURCE, subject, predicate, object.toString());
    }


    private static String createInsertQuery(URI graphName, Model graph){
        StmtIterator iterator = graph.listStatements();
        Set<String> statements = new LinkedHashSet<>();

        while(iterator.hasNext()){
            statements.add(createTriple(iterator.nextStatement()));
        }

        return createInsertQuery(graphName, statements);
    }


    @Override
    public ListenableFuture<Void> updateSensorValue(final URI graphName, final RDFNode sensorValue) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    updateSensorValue2(graphName, sensorValue);
                    result.set(null);
                } catch (Exception ex) {
                    LOG.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }

    @Override
    public ListenableFuture<QueryExecutionResults> processSparqlQuery(Query query){
        return processSparqlQuery(query.toString(Syntax.syntaxSPARQL));
    }


    private ListenableFuture<QueryExecutionResults> processSparqlQuery(final String query) {
        final SettableFuture<QueryExecutionResults> resultFuture = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {
            @Override
            public void process() {
                try {
                    LOG.debug("Start SPARQL query: \n{}", query);

                    long startTime = System.currentTimeMillis();
                    ResultSet resultSet = processSparqlQuery2(query);
                    long duration = System.currentTimeMillis() - startTime;
                    resultFuture.set(new QueryExecutionResults(duration, resultSet));

                    LOG.debug("Query execution finished (duration: {} millis)", duration);
                } catch (Exception e) {
                    LOG.error("Exception while processing SPARQL query.", e);
                    resultFuture.setException(e);
                } finally {
                    LOG.debug("Finished SPARQL query. \n{}");
                }
            }
        });

        return resultFuture;
    }

    @Override
    public ListenableFuture<Void> deleteNamedGraph(final URI graphName) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    LuposdateSemanticCache.this.evaluator.getResult(createDeleteQuery(graphName));
                    result.set(null);
                } catch (Exception ex) {
                    LOG.error("Exception while deleting graph {}!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }


    private void updateSensorValue2(URI graphName, RDFNode sensorValue) throws Exception{
        String updateQuery = "PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#>\n" +
                "DELETE {<" + graphName + "-SensorOutput> ssn:hasValue ?value }\n" +
                "INSERT {<" + graphName + "-SensorOutput> ssn:hasValue " + sensorValue.toString() + " }";

        this.evaluator.getResult(updateQuery);
    }


    private ResultSet processSparqlQuery2(String query) throws Exception{
        //Execute Query and make the result a JENA result set
        QueryResult queryResult = this.evaluator.getResult(query);
        return toResultSet(queryResult);
    }



    private ResultSet toResultSet(final QueryResult queryResult) throws IOException {
        long start = System.currentTimeMillis();

        XMLFormatter formatter = new XMLFormatter();

        //Solution with streams
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.writeResult(outputStream, queryResult.getVariableSet(), queryResult);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ResultSet resultSet = ResultSetFactory.fromXML(inputStream);

        LOG.debug("Time to re-format result-set: {} millis", System.currentTimeMillis() - start);

        return resultSet;
    }

    
    private abstract class DatabaseTask implements Runnable{
        
        @Override
        public void run(){
            int stillWaiting = waitingOperations.decrementAndGet();

            lock.lock();
            LOG.debug("Start DB operation (still waiting: {})", stillWaiting);
            process();
            lock.unlock();

            int finished = finishedOperations.incrementAndGet();
            if(LOG.isInfoEnabled() && (finished % 100 == 0 || stillWaiting == 0)){
                LOG.info("Finished DB operation #{} (still waiting: {}) ", finished, stillWaiting);
            }
        }
        
        public abstract void process();
    }

}
