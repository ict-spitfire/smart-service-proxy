//package eu.spitfire.ssp.server.handler.cache;
//
//import com.bbn.parliament.jena.joseki.client.RemoteModel;
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.query.Query;
//import com.hp.hpl.jena.rdf.model.Model;
//import com.hp.hpl.jena.rdf.model.RDFNode;
//import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
//import eu.spitfire.ssp.server.internal.messages.responses.QueryResult;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.net.URI;
//import java.util.Date;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.locks.ReentrantReadWriteLock;
//
///**
// * Created by olli on 15.07.14.
// */
//public class ParliamentSemanticCache extends SemanticCache{
//
//    private Logger log = LoggerFactory.getLogger(ParliamentSemanticCache.class.getName());
////    private RemoteModel model;
//    private ReentrantReadWriteLock lock;
//
//    public ParliamentSemanticCache(ExecutorService ioExecutorService,
//                                      ScheduledExecutorService internalTasksExecutorService) throws IOException {
//        super(ioExecutorService, internalTasksExecutorService);
//
//        RemoteModel model = new RemoteModel(
//                "http://localhost:8088/parliament/sparql",
//                "http://localhost:8088/parliament/bulk"
//        );
//
//        model.clearAll();
//
//        lock = new ReentrantReadWriteLock();
//    }
//
//    @Override
//    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {
//        SettableFuture<Boolean> resultFuture = SettableFuture.create();
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            resultFuture.set(model.askQuery(
//                    String.format("ASK {Graph <%s> {?s ?p ?o}}", graphName.toASCIIString())
//            ));
//
//            if(!resultFuture.get()){
//                model.dropNamedGraph(graphName.toASCIIString());
//            }
//            else{
//                log.error("Already contained...");
//            }
//            return resultFuture;
//        }
//        catch(Exception ex){
//            resultFuture.setException(ex);
//            return resultFuture;
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//    }
//
//    @Override
//    public ListenableFuture<ExpiringGraph> getNamedGraph(URI graphName) {
//        SettableFuture<ExpiringGraph> resultFuture = SettableFuture.create();
//
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            String query = String.format("CONSTRUCT { ?s ?p ?o } FROM <%s>", graphName.toASCIIString());
//
//            Model namedGraph = model.constructQuery(query);
//
//            resultFuture.set(new ExpiringGraph(namedGraph, new Date()));
//
//        }
//        catch(Exception ex){
//            resultFuture.setException(ex);
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//        return resultFuture;
//    }
//
//    @Override
//    public ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph) {
//        SettableFuture<Void> resultFuture = SettableFuture.create();
//
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            model.createNamedGraph(graphName.toASCIIString());
//            model.insertStatements(namedGraph, graphName.toASCIIString());
//
//            resultFuture.set(null);
//        }
//        catch(Exception ex){
//            log.error("Exception while putting named graph {} to cache!", graphName, ex);
//            resultFuture.setException(ex);
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//        return resultFuture;
//    }
//
//    @Override
//    public ListenableFuture<Void> updateSensorValue(URI graphName, RDFNode sensorValue) {
//        SettableFuture<Void> resultFuture = SettableFuture.create();
//
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            String updateQuery =  "WITH <" + graphName.toASCIIString() + ">\n" +
//                    "DELETE {<" + graphName + "-SensorOutput> ssn:hasValue ?value }\n" +
//                    "INSERT {<" + graphName + "-SensorOutput> ssn:hasValue " + sensorValue.toString() + " }";
//
//            model.updateQuery(updateQuery);
//
//            resultFuture.set(null);
//        }
//        catch(Exception ex){
//            log.error("Exception while putting named graph {} to cache!", graphName, ex);
//            resultFuture.setException(ex);
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//        return resultFuture;
//    }
//
//    @Override
//    public ListenableFuture<Void> deleteNamedGraph(URI graphName) {
//        SettableFuture<Void> resultFuture = SettableFuture.create();
//
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            model.dropNamedGraph(graphName.toASCIIString());
//            resultFuture.set(null);
//        }
//        catch(Exception ex){
//            log.error("Exception while deleting graph {}!", graphName.toASCIIString(), ex);
//            resultFuture.setException(ex);
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//
//        return resultFuture;
//    }
//
//    @Override
//    public ListenableFuture<QueryResult> processSparqlQuery(Query sparqlQuery) {
//        SettableFuture<QueryResult> resultFuture = SettableFuture.create();
//
//        try{
//            lock.writeLock().lock();
//
//            RemoteModel model = new RemoteModel(
//                    "http://localhost:8088/parliament/sparql",
//                    "http://localhost:8088/parliament/bulk"
//            );
//
//            QueryResult queryResult = new QueryResult(model.selectQuery(sparqlQuery));
//
//            resultFuture.set(queryResult);
//        }
//        catch(Exception ex){
//            log.error("Exception while executing SPARQL query \n{}!", sparqlQuery, ex);
//            resultFuture.setException(ex);
//        }
//        finally {
//            lock.writeLock().unlock();
//        }
//        return resultFuture;
//    }
//}
