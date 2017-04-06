package eu.spitfire.ssp.server.handler.cache;

import com.github.jsonldjava.core.RDFDataset;
import com.google.common.util.concurrent.*;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.utils.Converter;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.EvaluationHelper;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.evaluators.QueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import lupos.misc.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by olli on 17.06.14.
 */
public class LuposdateSemanticCache extends SemanticCache {

    private static Logger LOG = LoggerFactory.getLogger(LuposdateSemanticCache.class.getName());

    private QueryEvaluator evaluator;
    private AtomicInteger waitingOperations = new AtomicInteger(0);
    private AtomicInteger finishedOperations = new AtomicInteger(0);

    private ReentrantReadWriteLock lock;

    private ScheduledExecutorService cacheExecutor;

    public LuposdateSemanticCache(ExecutorService ioExecutorService,
                                  ScheduledExecutorService internalTasksExecutorService) {

        super(ioExecutorService, internalTasksExecutorService);
        this.initialize();
        this.lock = new ReentrantReadWriteLock();
        //this.cacheExecutor = Executors.newSingleThreadScheduledExecutor();
        this.cacheExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(
                "SSP Luposdate Thread #%d"
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
            queryEvaluator.prepareInputData(uriLiterals, new LinkedList<>());

            GeoFunctionRegisterer.registerGeoFunctions();
            EvaluationHelper.registerEvaluator("MIQE", MemoryIndexQueryEvaluator.class);
        }
        catch (Exception ex){
            LOG.error("This should never happen!", ex);
        }
    }

    public ScheduledExecutorService getInternalTasksExecutor(){
        return this.cacheExecutor;
    }

//    private QueryEvaluator getRuleEvaluator() throws Exception{
////        return this.evaluator;
//
//        BufferedReader reader = new BufferedReader(new FileReader("/home/olli/ssp-files/inference-test/geonames.ttl"));
//        String data = "";
//        String line = reader.readLine();
//        while(line != null){
//            data += line;
//            line = reader.readLine();
//        }
//
//
//
//        EvaluationHelper.SPARQLINFERENCE inference = EvaluationHelper.SPARQLINFERENCE.OWL2RL;
//        final String inferenceRules = inference.getRuleSet(new EvaluationHelper.RuleSets("/rif/"),
//            EvaluationHelper.GENERATION.GENERATEDOPT, false, data, null);
//
//        if(inferenceRules != null){
//            final BasicIndexRuleEvaluator bire = new BasicIndexRuleEvaluator((MemoryIndexQueryEvaluator) evaluator);
//            bire.compileQuery(inferenceRules);
//
//            evaluator.logicalOptimization();
//            bire.physicalOptimization();
//
//            RuleResult errorsInOntology = bire.inferTriplesAndStoreInDataset();
//
//            // compile and optimize query/ruleset
//            evaluator.compileQuery(query);
//            evaluator.logicalOptimization();
//            evaluator.physicalOptimization();
//
//            QueryResult[] result;
//            // start evaluation...
//            if (evaluator instanceof CommonCoreQueryEvaluator || evaluator instanceof BasicIndexRuleEvaluator) {
//                final CollectRIFResult crr = new CollectRIFResult(false);
//                final Result resultOperator = (evaluator instanceof CommonCoreQueryEvaluator)?((CommonCoreQueryEvaluator<Node>)evaluator).getResultOperator(): ((BasicIndexRuleEvaluator)evaluator).getResultOperator();
//                resultOperator.addApplication(crr);
//                evaluator.evaluateQuery();
//                result = crr.getQueryResults();
//            } else {
//                result = new QueryResult[1];
//                result[0] = evaluator.getResult();
//            }
//            if(errorsInOntology!=null) {
//                final QueryResult[] iresult = result;
//                result = new QueryResult[result.length+1];
//                System.arraycopy(iresult, 0, result, 0, iresult.length);
//                result[result.length-1] = errorsInOntology;
//            }
//
//
//        basicIndexRuleEvaluator.prepareInputData("/home/olli/ssp-files/ontology/ontology_v3.1.rdf");
//
//        return basicIndexRuleEvaluator;
//    }

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

    @Override
    public ListenableFuture<ExpiringGraph> getDefaultGraph() {
        final SettableFuture<ExpiringGraph> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        LOG.debug("Wait for DB (now waiting: {})", waiting);

        ListenableFuture<QueryExecutionResults> future = processSparqlQuery("SELECT ?s ?p ?o WHERE {?s ?p ?o}");
        Futures.addCallback(future, new FutureCallback<QueryExecutionResults>() {
            @Override
            public void onSuccess(QueryExecutionResults queryExecutionResults) {
                result.set(new ExpiringGraph(Converter.toModel(queryExecutionResults.getResultSet())));
            }

            @Override
            public void onFailure(Throwable throwable) {
                result.setException(throwable);
            }
        });
//        this.getInternalTasksExecutor().execute(new DatabaseTask() {
//
//            @Override
//            public void process() {
//                try {
//                    result.set(getDefaultGraph2());
//                } catch (Exception ex) {
//                    LOG.error("Exception while getting default graph!", ex);
//                    result.setException(ex);
//                }
//            }
//        });

        return result;
    }


    private ExpiringNamedGraph getNamedGraph2(URI graphName) throws Exception{
        String query = "SELECT ?s ?p ?o  WHERE {GRAPH <" + graphName + "> { ?s ?p ?o }}";

        QueryResult result = this.evaluator.getResult(query);
        Model model = Converter.toModel(toResultSet(result));

        return new ExpiringNamedGraph(graphName, model, new Date());
    }

    private ExpiringGraph getDefaultGraph2() throws Exception{
        String query = "SELECT ?s ?p ?o  WHERE { ?s ?p ?o }";

        ListenableFuture<QueryExecutionResults> future = processSparqlQuery(query);
        Futures.addCallback(future, new FutureCallback<QueryExecutionResults>() {
            @Override
            public void onSuccess(QueryExecutionResults queryExecutionResults) {

            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });

        QueryResult result = this.evaluator.getResult(query);
        Model model = Converter.toModel(toResultSet(result));

        return new ExpiringGraph(model, new Date());
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
                ).replace("\n", " ");
            }
            //untyped literal
            return String.format(
                Locale.ENGLISH, TRIPLE_TEMPLATE_WITH_UNTYPED_LITERAL, subject, predicate, object.toString()
            ).replace("\n", " ");
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
                    //ResultSet resultSet = processSparqlQuery2(query, !query.contains("GRAPH"));
                    ResultSet resultSet = processSparqlQuery2(query);
                    long duration = System.currentTimeMillis() - startTime;
                    resultFuture.set(new QueryExecutionResults(duration, resultSet));

                    LOG.debug("Query execution finished (duration: {} millis)", duration);
                } catch (Exception e) {
                    LOG.error("Exception while processing SPARQL query.", e);
                    resultFuture.setException(e);
                } catch (Error e) {
                    LOG.error("Error while processing SPARQL query.", e);
                    resultFuture.setException(e);
                } finally {
                    LOG.debug("Finished SPARQL query. \n");
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
//        //Execute Query and make the result a JENA result set
//        QueryResult queryResult = this.getRuleEvaluator().getResult(query);
//        return toResultSet(queryResult);

//        if(!query.contains("GRAPH")) {
//            BufferedReader reader = new BufferedReader(new FileReader("/home/olli/ssp-files/acme-luposdate/ontology.ttl"));
//            String data = "";
//            String line = reader.readLine();
//            while(line != null){
//                data += "\n" + line;
//                line = reader.readLine();
//            }
//            reader.close();
//
//            long start = System.nanoTime();
//            int index = EvaluationHelper.getEvaluatorIndexByName("MIQE");
//            EvaluationHelper.SPARQLINFERENCE inf = EvaluationHelper.SPARQLINFERENCE.OWL2RL;
//            EvaluationHelper.GENERATION gen = EvaluationHelper.GENERATION.GENERATEDOPT;
//            EvaluationHelper.SPARQLINFERENCEMATERIALIZATION mat = EvaluationHelper.SPARQLINFERENCEMATERIALIZATION.MATERIALIZEALL;
//
//            Tuple<String, QueryResult[]> results = EvaluationHelper.getQueryResult(
//                index, false, inf, gen, mat, false, data, null, query
//            );
//            LOG.info("Execution duration (incl. inference): {} ns.", System.nanoTime() - start);
//            return toResultSet(results.getSecond()[0]);
//
//        } else {
            QueryResult result = this.evaluator.getResult(query);
            return toResultSet(result);
//        }
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
            try {
                lock.writeLock().lock();
                LOG.debug("Start DB operation (still waiting: {})", stillWaiting);
                this.process();
                int finished = finishedOperations.incrementAndGet();
                if (LOG.isInfoEnabled() && (finished % 100 == 0 || stillWaiting == 0)) {
                    LOG.info("Finished DB operation #{} (still waiting: {}) ", finished, stillWaiting);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public abstract void process();
    }


	@Override
	protected ScheduledExecutorService getCacheTasksExecutor() {
		return this.getInternalTasksExecutor();
	}

}
