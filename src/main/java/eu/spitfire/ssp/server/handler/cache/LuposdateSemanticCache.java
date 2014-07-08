package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.*;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import eu.spitfire.ssp.backends.generic.messages.ExpiringGraphHttpResponse;
import eu.spitfire.ssp.server.common.messages.SparqlQueryResultMessage;
import eu.spitfire.ssp.server.internal.messages.ExpiringGraph;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.datastructures.queryresult.BooleanResult;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.BasicIndexQueryEvaluator;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
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
    private AtomicInteger startetQueries = new AtomicInteger(0);
    private AtomicInteger finishedQueries = new AtomicInteger(0);

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
            queryEvaluator.getArgs().set("result",          QueryResult.TYPE.MEMORY);
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

            GeoFunctionRegisterer.registerGeoFunctions();
        }
        catch (Exception ex){
            log.error("This should never happen!", ex);
        }
    }


    @Override
    public ListenableFuture<Boolean> containsNamedGraph(final URI graphName) {
        final SettableFuture<Boolean> containsFuture = SettableFuture.create();

        this.cacheExecutor.execute(new Runnable(){

            @Override
            public void run() {
                try{
                    containsFuture.set(containsNamedGraph2(graphName));

                }
                catch(Exception ex){
                    log.error("Exception while checking whether graph {} is contained.", graphName, ex);
                    containsFuture.setException(ex);
                }
            }
        });

        return containsFuture;
    }


    @Override
    public ListenableFuture<ExpiringGraphHttpResponse> getNamedGraph(final URI graphName) {
        final SettableFuture<ExpiringGraphHttpResponse> result = SettableFuture.create();

        this.cacheExecutor.execute(new Runnable(){

            @Override
            public void run() {
                try{
                    if(!containsNamedGraph2(graphName)){
                        result.set(null);
                    }

                    result.set(getNamedGraph2(graphName));
                }
                catch(Exception ex){
                    result.setException(ex);
                }
            }
        });

        return result;

    }


    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, final Model namedGraph) {
        final SettableFuture<Void> result = SettableFuture.create();

        this.cacheExecutor.execute(new Runnable(){

            @Override
            public void run() {
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
    public ListenableFuture<SparqlQueryResultMessage> processSparqlQuery(final Query sparqlQuery) {
        final SettableFuture<SparqlQueryResultMessage> resultFuture = SettableFuture.create();

        this.cacheExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    log.info("Start SPARQL query (#{}): \n{}", startetQueries.incrementAndGet() ,
                            sparqlQuery.toString());

                    SparqlQueryResultMessage resultMessage = processSparqlQuery2(sparqlQuery);
                    resultFuture.set(resultMessage);
                }
                catch (Exception e) {
                    log.error("Exception while processing SPARQL query.", e);
                    resultFuture.setException(e);
                }
                finally {
                    log.info("Finished SPARQL query (#{}).", finishedQueries.incrementAndGet());
                }
            }
        });

        return resultFuture;
    }


    @Override
    public ListenableFuture<Void> deleteNamedGraph(final URI graphName) {
        final SettableFuture<Void> result = SettableFuture.create();

        this.cacheExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    if(!deleteNamedGraph2(graphName)){
                        log.error("Could not delete graph {} from cache!", graphName);
                    }

                    result.set(null);

                }
                catch(Exception ex){
                    log.error("Exception while deleting graph {} from cache!", graphName, ex);
                    result.setException(ex);
                }
            }
        });

        return result;
    }

    private boolean containsNamedGraph2(URI graphName) throws Exception {
        BooleanResult queryResult = (BooleanResult) this.evaluator.getResult(String.format(
                "ASK {Graph <%s> {?s ?p ?o}}", graphName)
        );

        return queryResult.isTrue();
    }

    private void putNamedGraphToCache2(URI graphName, Model namedGraph) throws Exception{
        if(!deleteNamedGraph2(graphName))
            return;

        StmtIterator stmtIterator = namedGraph.listStatements();

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("INSERT DATA {\n").append("GRAPH <").append(graphName).append(">\n\t{\n");

        while(stmtIterator.hasNext()){
            Statement statement = stmtIterator.nextStatement();
            queryBuilder.append("\t\t<").append(statement.getSubject().toString()).append("> <")
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

        queryBuilder.append("\t}\n}");

        String query = queryBuilder.toString();
        log.debug("Query for inserting data:\n{}\n", query);
        this.evaluator.getResult(query);

        URILiteral uriLiteral = LiteralFactory.createURILiteralWithoutLazyLiteral("<" + graphName.toString() + ">");
        Indices indices = this.evaluator.getDataset().getNamedGraphIndices(uriLiteral);
        this.evaluator.getDataset().putIntoDefaultGraphs(uriLiteral, indices);
    }

    private ExpiringGraphHttpResponse getNamedGraph2(URI graphName) throws Exception{
        String query = String.format("SELECT ?s ?p ?o FROM <%s> WHERE {?s ?p ?o}", graphName);
        QueryResult queryResult = this.evaluator.getResult(query);

        ListenableFuture<ResultSet> resultSetFuture = toResultSet(queryResult);
        ResultSet resultSet = resultSetFuture.get();
        Model model = toModel(resultSet);

        return new ExpiringGraphHttpResponse(HttpResponseStatus.OK, new ExpiringGraph(model, new Date()));
    }


    private boolean deleteNamedGraph2(URI graphName) throws Exception{
        if(containsNamedGraph2(graphName)){
            this.evaluator.getResult(String.format("DROP GRAPH <%s>", graphName));
        }

        return !containsNamedGraph2(graphName);
    }


    private SparqlQueryResultMessage processSparqlQuery2(Query sparqlQuery) throws Exception{
        final SettableFuture<SparqlQueryResultMessage> messageFuture = SettableFuture.create();

        QueryResult queryResult  = this.evaluator.getResult(sparqlQuery.toString());
        log.info("Result received!");
        ListenableFuture<ResultSet> resultSetFuture = toResultSet(queryResult);
        Futures.addCallback(resultSetFuture, new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(@Nullable ResultSet resultSet) {
                try{
                    SparqlQueryResultMessage sparqlQueryResultMessage = new SparqlQueryResultMessage(resultSet);
                    messageFuture.set(sparqlQueryResultMessage);
                }
                catch(Exception ex){
                    log.error("Exception while creating internal SPARQL query result message.", ex);
                    messageFuture.setException(ex);
                }

            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Unexpected Exception!", t);
                messageFuture.setException(t);
            }
        });

        return messageFuture.get();
    }

    private Model toModel(ResultSet resultSet){
        Model model = ModelFactory.createDefaultModel();

        if(resultSet == null){
            log.error("ResultSet was NULL!!!");
            return null;
        }


        while(resultSet.hasNext()){
            Binding binding = resultSet.nextBinding();

            com.hp.hpl.jena.graph.Node subject = binding.get(Var.alloc("s"));
            com.hp.hpl.jena.graph.Node predicate = binding.get(Var.alloc("p"));
            com.hp.hpl.jena.graph.Node object = binding.get(Var.alloc("o"));

            Resource s = subject.isBlank() ? ResourceFactory.createResource(subject.getBlankNodeLabel()) :
                    ResourceFactory.createResource(subject.getURI());

            Property p =  ResourceFactory.createProperty(predicate.getURI());

            RDFNode o = object.isBlank() ? ResourceFactory.createResource(object.getBlankNodeLabel()) :
                    object.isLiteral() ? ResourceFactory.createPlainLiteral(object.getLiteralLexicalForm()) :
                            ResourceFactory.createResource(object.getURI());

            model.add(model.createStatement(s, p, o));

        }

        return model;
    }


    private ListenableFuture<ResultSet> toResultSet(final QueryResult queryResult) throws IOException {

        final SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();
        final File tmpFile = File.createTempFile("queryResult", ".xml");

        XMLFormatter formatter = new XMLFormatter();
        formatter.writeResult(new FileOutputStream(tmpFile), queryResult.getVariableSet(), queryResult);

        ResultSet result = ResultSetFactory.fromXML(new FileInputStream(tmpFile));
        resultSetFuture.set(result);

        tmpFile.delete();

        return resultSetFuture;


    }


//    private ListenableFuture<ResultSet> toResultSet(final QueryResult queryResult) throws IOException {
//
//        final SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();
////        final File tmpFile = File.createTempFile("queryResult", ".xml");
//
//        final PipedOutputStream outputStream = new PipedOutputStream();
//        final PipedInputStream inputStream = new PipedInputStream(outputStream);
//
//        this.getInternalTasksExecutor().execute(new Runnable(){
//            @Override
//            public void run() {
//                try{
//
//                    XMLFormatter formatter = new XMLFormatter();
//                    formatter.writeResult(outputStream, queryResult.getVariableSet(), queryResult);
//                }
//                catch(Exception ex){
//                    log.error("Error while writing SPARQL query result to output stream...", ex);
//                    resultSetFuture.setException(ex);
//                }
//            }
//        });
//
//        this.getInternalTasksExecutor().execute(new Runnable() {
//            @Override
//            public void run() {
//                try{
//                    ResultSet result = ResultSetFactory.fromXML(inputStream);
//                    resultSetFuture.set(result);
//
//
//                }
//                catch(Exception ex){
//                    log.error("Error while reading SPARQL query result from input stream...", ex);
//                    resultSetFuture.setException(ex);
//                }
//            }
//        });
//
//        return resultSetFuture;
//    }

}
