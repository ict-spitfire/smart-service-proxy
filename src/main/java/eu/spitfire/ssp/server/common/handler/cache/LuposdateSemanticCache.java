package eu.spitfire.ssp.server.common.handler.cache;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import eu.spitfire.ssp.server.common.messages.ExpiringGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryResultMessage;
import eu.spitfire.ssp.server.common.wrapper.ExpiringGraph;
import lupos.datastructures.items.literal.*;
import lupos.datastructures.queryresult.BooleanResult;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import lupos.sparql1_1.Node;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by olli on 17.06.14.
 */
public class LuposdateSemanticCache extends SemanticCache {

    private static Logger log = LoggerFactory.getLogger(LuposdateSemanticCache.class.getName());

    private CommonCoreQueryEvaluator<Node> evaluator;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LuposdateSemanticCache(ExecutorService ioExecutorService,
                                  ScheduledExecutorService internalTasksExecutorService) {

        super(ioExecutorService, internalTasksExecutorService);
        this.initialize();
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
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {
        lock.readLock().lock();
        SettableFuture<Boolean> containsFuture = SettableFuture.create();
        try{
            containsFuture.set(containsNamedGraph2(graphName));
            return containsFuture;
        }
        catch(Exception ex){
            log.error("Exception while checking whether graph {} is contained.", graphName, ex);
            containsFuture.setException(ex);
            return containsFuture;
        }
        finally {
            lock.readLock().unlock();
        }
    }


    @Override
    public ListenableFuture<ExpiringGraphStatusMessage> getNamedGraph(URI graphName) {
        lock.readLock().lock();
        final SettableFuture<ExpiringGraphStatusMessage> result = SettableFuture.create();
        try{
            if(!containsNamedGraph2(graphName)){
                result.set(null);
                return result;
            }

            result.set(getNamedGraph2(graphName));
            return result;
        }
        catch(Exception ex){
            result.setException(ex);
            return result;
        }
        finally {
            lock.readLock().unlock();
        }
    }


    @Override
    public ListenableFuture<Void> putNamedGraphToCache(final URI graphName, final Model namedGraph) {
        lock.writeLock().lock();
        final SettableFuture<Void> result = SettableFuture.create();
        try{
            putNamedGraphToCache2(graphName, namedGraph);
            result.set(null);
            return result;
        }
        catch (Exception ex){
            log.error("Exception while putting graph {} to cache!", graphName, ex);
            result.setException(ex);
            return result;
        }
        finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    public synchronized ListenableFuture<SparqlQueryResultMessage> processSparqlQuery(Query sparqlQuery) {
        if(sparqlQuery.isAskType() || sparqlQuery.isSelectType()){
            lock.readLock().lock();
        }
        else{
            lock.writeLock().lock();
        }

        final SettableFuture<SparqlQueryResultMessage> resultFuture = SettableFuture.create();

        try{
            SparqlQueryResultMessage resultMessage = processSparqlQuery2(sparqlQuery);
            resultFuture.set(resultMessage);
            return resultFuture;
        }
        catch (Exception e) {
            log.error("This should never happen.", e);

            resultFuture.setException(e);
            return resultFuture;
        }
        finally {
            if(sparqlQuery.isAskType() || sparqlQuery.isSelectType()){
                lock.readLock().unlock();
            }
            else{
                lock.writeLock().unlock();
            }
        }
    }


    @Override
    public ListenableFuture<Void> deleteNamedGraph(URI graphName) {
        SettableFuture<Void> result = SettableFuture.create();

        try{
            if(!deleteNamedGraph2(graphName)){
                log.error("Could not delete graph {} from cache!", graphName);
            }

            result.set(null);
            return result;
        }
        catch(Exception ex){
            log.error("Exception while deleting graph {} from cache!", graphName, ex);
            result.setException(ex);
            return result;
        }
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
        LuposdateSemanticCache.this.evaluator.getResult(query);
    }

    private ExpiringGraphStatusMessage getNamedGraph2(URI graphName) throws Exception{
        String query = String.format("SELECT ?s ?p ?o FROM <%s> WHERE {?s ?p ?o}", graphName);
        QueryResult queryResult = this.evaluator.getResult(query);

        ListenableFuture<ResultSet> resultSetFuture = toResultSet(queryResult);
        ResultSet resultSet = resultSetFuture.get();
        Model model = toModel(resultSet);

        return new ExpiringGraphStatusMessage(HttpResponseStatus.OK, new ExpiringGraph(model, new Date()));
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
        final PipedOutputStream outputStream = new PipedOutputStream();
        final PipedInputStream inputStream = new PipedInputStream(outputStream);

        this.getInternalTasksExecutorService().execute(new Runnable(){
            @Override
            public void run() {
                try{
                    XMLFormatter formatter = new XMLFormatter();
                    formatter.writeResult(outputStream, queryResult.getVariableSet(), queryResult);
                }
                catch(Exception ex){
                    log.error("Error while writing SPARQL query result to output stream...", ex);
                    resultSetFuture.setException(ex);
                }
            }
        });

        this.getInternalTasksExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                try{
                    ResultSet result = ResultSetFactory.fromXML(inputStream);
                    resultSetFuture.set(result);


                }
                catch(Exception ex){
                    log.error("Error while reading SPARQL query result from input stream...", ex);
                    resultSetFuture.setException(ex);
                }
            }
        });

        return resultSetFuture;
    }

}
