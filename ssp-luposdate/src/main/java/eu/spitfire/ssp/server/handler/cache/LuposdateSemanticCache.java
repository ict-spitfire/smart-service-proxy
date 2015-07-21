package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.*;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.BasicIndexQueryEvaluator;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.evaluators.RDF3XQueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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

    private static Logger log = LoggerFactory.getLogger(LuposdateSemanticCache.class.getName());

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
    public ListenableFuture<ExpiringNamedGraph> getNamedGraph(final URI graphName) {
        final SettableFuture<ExpiringNamedGraph> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    result.set(getNamedGraph2(graphName));
                } catch (Exception ex) {
                    log.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }


    private ExpiringNamedGraph getNamedGraph2(URI graphName) throws Exception{
        String query = "SELECT ?s ?p ?o WHERE {GRAPH <" + graphName + "> { ?s ?p ?o }}";

        QueryResult result = this.evaluator.getResult(query);
        Model graph = toModel(toResultSet(result));

        return new ExpiringNamedGraph(graphName, graph, new Date());
    }



    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, final Model namedGraph) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    putNamedGraphToCache2(graphName, namedGraph);
                    result.set(null);
                } catch (Exception ex) {
                    log.error("Exception while putting graph {} to cache!", graphName, ex);
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
        log.info("Deleted graph \"{}\" (duration: {} ms)", graphName, System.currentTimeMillis() - start);

        //insert new triples from given graph
        this.evaluator.getResult(createInsertQuery(graphName, graph));
        log.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
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
        log.debug("\n" + query);
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
        log.debug("\n" + query);
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

    private static String createInsertQueryOld(URI graphName, Model graph){

        //create triples to be inserted
        StmtIterator statements = graph.listStatements();
        StringBuilder triples = new StringBuilder();
        while(statements.hasNext()){
            Statement statement = statements.nextStatement();
            triples.append("\t<").append(statement.getSubject().toString()).append("> <")
                    .append(statement.getPredicate().toString()).append("> ");

            if(statement.getObject().isLiteral()){
                if(statement.getObject().toString().contains("^^")){
                    String[] parts = statement.getObject().toString().split("\\^\\^");
                    triples.append("\"").append(parts[0]).append("\"^^<").append(parts[1]).append("> .\n");
                }
                else{
                    triples.append("\"").append(statement.getObject().toString()).append("\" .\n");
                }
            }

            else{
                triples.append("<").append(statement.getObject().toString()).append("> .\n");
            }
        }

        String query = "INSERT DATA {\n\tGRAPH <" + graphName + "> {\n\t" + triples.toString() + " }\n" +
                        triples.toString() + " } ";

        log.info("\n" + query);
        return query;

    }


    @Override
    public ListenableFuture<Void> updateSensorValue(final URI graphName, final RDFNode sensorValue) {
        final SettableFuture<Void> result = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {

            @Override
            public void process() {
                try {
                    updateSensorValue2(graphName, sensorValue);
                    result.set(null);
                } catch (Exception ex) {
                    log.error("Exception while putting graph {} to cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }

    @Override
    public ListenableFuture<ResultSet> processSparqlQuery(Query query){
        return processSparqlQuery(query.toString(Syntax.syntaxSPARQL));
    }


    private ListenableFuture<ResultSet> processSparqlQuery(final String query) {
        final SettableFuture<ResultSet> resultFuture = SettableFuture.create();

        int waiting = waitingOperations.incrementAndGet();
        log.debug("Wait for DB thread (now waiting: {})", waiting);

        this.getInternalTasksExecutor().execute(new DatabaseTask() {
            @Override
            public void process() {
                try {
                    log.debug("Start SPARQL query: \n{}", query);
                    long startTime = System.currentTimeMillis();
                    resultFuture.set(processSparqlQuery2(query));
                    log.debug("Query execution finished (duration: {} millis)", System.currentTimeMillis() - startTime);
                } catch (Exception e) {
                    log.error("Exception while processing SPARQL query.", e);
                    resultFuture.setException(e);
                } finally {
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


    private ResultSet processSparqlQuery2(String query) throws Exception{
        //Execute Query and make the result a JENA result set
        QueryResult queryResult = this.evaluator.getResult(query);
        return toResultSet(queryResult);
    }


    private Model toModel(ResultSet resultSet){
        Model model = ModelFactory.createDefaultModel();

        if(resultSet == null){
            log.error("ResultSet was NULL!!!");
            return null;
        }


        while(resultSet.hasNext()){
            Binding binding = resultSet.nextBinding();

            Node subject = binding.get(Var.alloc("s"));
            Node predicate = binding.get(Var.alloc("p"));
            Node object = binding.get(Var.alloc("o"));

            Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
                    ResourceFactory.createResource(subject.getURI());

            Property p =  ResourceFactory.createProperty(predicate.getURI());

            RDFNode o = createRDFNode(object);
//            if(object.isBlank()){
//                o = ResourceFactory.createResource(object.getBlankNodeLabel());
//            }
//            else if(!object.isLiteral()){
//                ResourceFactory.createResource(object.getURI());
//            }? :
//                    !object.getLiteralDatatype().equals(null) ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm());

            model.add(model.createStatement(s, p, o));

        }

        return model;
    }

    private static RDFNode createRDFNode(Node object){
        if(object.isBlank())
            return ResourceFactory.createResource(object.getBlankNodeLabel());

        if(!object.isLiteral())
            return ResourceFactory.createResource(object.getURI());

        RDFDatatype datatype = object.getLiteralDatatype();
        if(datatype.equals(null)){
            return ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm());
        }

        return ResourceFactory.createTypedLiteral(object.getLiteralLexicalForm(), datatype);
    }

    private ResultSet toResultSet(final QueryResult queryResult) throws IOException {
        long start = System.currentTimeMillis();

//        final File tmpFile = File.createTempFile("queryResult", ".xml");

        XMLFormatter formatter = new XMLFormatter();

        //Solution with streams
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.writeResult(outputStream, queryResult.getVariableSet(), queryResult);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ResultSet resultSet = ResultSetFactory.fromXML(inputStream);

        //Solution with TMP files (works!)
//        formatter.writeResult(new FileOutputStream(tmpFile), queryResult.getVariableSet(), queryResult);
//        ResultSet resultSet = ResultSetFactory.fromXML(new FileInputStream(tmpFile));

//        if(!tmpFile.delete()){
//            log.error("Temporary file {} could not deleted...", tmpFile.getAbsolutePath());
//        }

        log.debug("Time to re-format result-set: {} millis", System.currentTimeMillis() - start);
        return resultSet;
    }

    
    private abstract class DatabaseTask implements Runnable{
        
        @Override
        public void run(){
            int stillWaiting = waitingOperations.decrementAndGet();

            lock.lock();
            log.debug("Start DB operation (still waiting: {})", stillWaiting);
            process();
            lock.unlock();

            int finished = finishedOperations.incrementAndGet();
            if(log.isInfoEnabled() && (finished % 100 == 0 || stillWaiting == 0)){
                log.info("Finished DB operation #{} (still waiting: {}) ", finished, stillWaiting);
            }
        }
        
        public abstract void process();
    }

}
