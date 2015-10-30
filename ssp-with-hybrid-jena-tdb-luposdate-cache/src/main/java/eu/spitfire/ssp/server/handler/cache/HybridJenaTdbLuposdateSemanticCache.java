package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import com.hp.hpl.jena.shared.impl.JenaParameters;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.utils.Converter;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import lupos.datastructures.items.literal.LiteralFactory;
import lupos.datastructures.items.literal.URILiteral;
import lupos.datastructures.queryresult.QueryResult;
import lupos.endpoint.server.format.XMLFormatter;
import lupos.engine.evaluators.CommonCoreQueryEvaluator;
import lupos.engine.evaluators.MemoryIndexQueryEvaluator;
import lupos.engine.evaluators.QueryEvaluator;
import lupos.engine.operators.index.Indices;
import lupos.geo.geosparql.GeoFunctionRegisterer;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
* The Jena TDB Cache does *NOT* support GeoSPARQL but "Jena Spatial", i.e. a subset of the GeoSPARQL features with a
* different vocabulary!
*
* @author Oliver Kleine
*/
public class HybridJenaTdbLuposdateSemanticCache extends SemanticCache {

    private static Logger LOG = LoggerFactory.getLogger(HybridJenaTdbLuposdateSemanticCache.class.getName());

    private Dataset dataset;
	private Reasoner reasoner;

	private ReentrantReadWriteLock lock;
	private QueryEvaluator queryEvaluator;

	public HybridJenaTdbLuposdateSemanticCache(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
											   String tdbDirectory, Set<String> ontologyPaths){

		super(ioExecutor, internalTasksExecutor);

		this.lock = new ReentrantReadWriteLock();

        //Enable acceptence of literals having an unknown XSD datatype
        JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = true;

        //Disable acceptence of literals having an illegal value for the given XSD datatype
        JenaParameters.enableEagerLiteralValidation = false;

		File directory = new File(tdbDirectory);
		File[] oldFiles = directory.listFiles();

        assert oldFiles != null;
        for (File dbFile : oldFiles) {
            if(!dbFile.delete()){
                LOG.error("Could not delete old DB file: {}", dbFile);
            }
        }

		dataset = TDBFactory.createDataset(tdbDirectory);
		TDB.getContext().set(TDB.symUnionDefaultGraph, true);

		//Build the reasoner to infer new statements
		OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM);
		for(String ontologyPath : ontologyPaths) {
			try {
				if(!(new File(ontologyPath).isDirectory()) && ontologyPath.endsWith(".ttl")) {
					LOG.info("Read ontology: {}", ontologyPath);
					FileInputStream inputStream = new FileInputStream(ontologyPath);
					//ontologyModel.read(inputStream, "TTL");
					ontologyModel.read(ontologyPath);
					inputStream.close();
					LOG.info("Successfully read ontology: {}", ontologyPath);
				}

			} catch (Exception ex) {
				LOG.error("Error while reading... ontology: {}", ontologyPath, ex);
			}
		}
		this.reasoner = ReasonerRegistry.getOWLReasoner().bindSchema(ontologyModel);
		LOG.info("Reasoner created!");

		this.getInternalTasksExecutor().scheduleAtFixedRate(new InferenceTask(), 0, 30, TimeUnit.SECONDS);
	}

	@Override
	public ListenableFuture<ExpiringNamedGraph> getNamedGraph(URI graphName) {

        SettableFuture<ExpiringNamedGraph> resultFuture = SettableFuture.create();

        try {
			lockDataset();

			if (graphName == null){
				LOG.error("Resource URI was NULL!");
                resultFuture.set(null);
                return resultFuture;
            }

			Model dbModel = dataset.getNamedModel(graphName.toString());

			if (dbModel.isEmpty()) {
				LOG.warn("No cached status found for resource {}", graphName);
				resultFuture.set(null);
			}

            else{
                LOG.info("Cached status found for resource {}", graphName);

				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				RDFDataMgr.write(outputStream, dbModel, RDFFormat.TURTLE);

				ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
				Model outModel = ModelFactory.createDefaultModel();
				RDFDataMgr.read(outModel, inputStream, RDFFormat.TURTLE.getLang());
				inputStream.close();

                resultFuture.set(new ExpiringNamedGraph(graphName, outModel, new Date()));
            }

            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			unlockDataset();
		}

	}

	@Override
	public ListenableFuture<ExpiringGraph> getDefaultGraph() {
		SettableFuture<ExpiringGraph> future = SettableFuture.create();
	    try{
	    	lockDataset();
			Query query = QueryFactory.create("SELECT ?s ?p ?o WHERE {?s ?p ?o}");
			ListenableFuture<QueryExecutionResults> resultsFuture = processSparqlQuery(query);

			Futures.addCallback(resultsFuture, new FutureCallback<QueryExecutionResults>() {
				@Override
				public void onSuccess(QueryExecutionResults queryExecutionResults) {
					Model model = Converter.toModel(queryExecutionResults.getResultSet());
					future.set(new ExpiringGraph(model, new Date()));
				}

				@Override
				public void onFailure(Throwable throwable) {
					future.setException(throwable);
				}
			});
		} catch(Exception ex) {
			LOG.error("Could not read default (union) graph: {}", ex);
			future.setException(ex);
		} finally {
			unlockDataset();
		}

		return future;
	}

	@Override
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {

        SettableFuture<Boolean> resultFuture = SettableFuture.create();

        try{
			lockDataset();
            //dataset.begin(ReadWrite.READ);
            Boolean result = !dataset.getNamedModel(graphName.toString()).isEmpty();
            resultFuture.set(result);
            return resultFuture;
        } catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			unlockDataset();
            //dataset.end();
        }
    }


    @Override
	public ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph){

        SettableFuture<Void> resultFuture = SettableFuture.create();

        try {
			lockDataset();
            long start = System.currentTimeMillis();
			dataset.removeNamedModel(graphName.toString());
			LOG.info("Removed old graph \"{}\" (duration: {} ms)", graphName, System.currentTimeMillis() - start);
			dataset.addNamedModel(graphName.toString(), namedGraph);
			LOG.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);

            resultFuture.set(null);
            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
 			unlockDataset();
		}
	}


    @Override
    public ListenableFuture<Void> updateSensorValue(URI graphName, RDFNode sensorValue) {
        //TODO!!!
        SettableFuture<Void> resultFuture = SettableFuture.create();
        resultFuture.set(null);

        return resultFuture;
    }


    @Override
	public ListenableFuture<Void> deleteNamedGraph(URI graphName){

		SettableFuture<Void> resultFuture = SettableFuture.create();

        try {
			this.lock.writeLock().lock();
            //dataset.begin(ReadWrite.WRITE);
			dataset.removeNamedModel(graphName.toString());
			dataset.commit();
			LOG.debug("Removed status for resource {}", graphName);
            resultFuture.set(null);
            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			this.lock.writeLock().unlock();
			//dataset.end();
		}
	}

//	private QueryEvaluator getValidQueryEvaluator() throws Exception {
//		try {
//			this.lock.writeLock().lock();
//			if(isEvaluatorRecreationNecessary()) {
//				InfModel infModel = ModelFactory.createInfModel(reasoner, dataset.getNamedModel("urn:x-arq:UnionGraph"));
//
//				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//				RDFDataMgr.write(outputStream, infModel, RDFFormat.TURTLE_FLAT);
//
//				GeoFunctionRegisterer.registerGeoFunctions();
//				this.queryEvaluator = new MemoryIndexQueryEvaluator();
//
//				this.queryEvaluator.setupArguments();
//				this.queryEvaluator.getArgs().set("result", lupos.datastructures.queryresult.QueryResult.TYPE.MEMORY);
//				this.queryEvaluator.getArgs().set("codemap", LiteralFactory.MapType.TRIEMAP);
//				this.queryEvaluator.getArgs().set("distinct", CommonCoreQueryEvaluator.DISTINCT.HASHSET);
//				this.queryEvaluator.getArgs().set("join", CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
//				this.queryEvaluator.getArgs().set("optional", CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
//				this.queryEvaluator.getArgs().set("datastructure", Indices.DATA_STRUCT.HASHMAP);
//
//				this.queryEvaluator.init();
//
//				Collection<URILiteral> uriLiterals = new LinkedList<>();
//				uriLiterals.add(LiteralFactory.createStringURILiteral(
//								"<inlinedata: " + new String(outputStream.toByteArray()) + ">")
//				);
//
//				this.queryEvaluator.prepareInputData(uriLiterals, new LinkedList<>());
//			}
//			setEvaluatorRecreationNecessary(false);
//			return this.queryEvaluator;
//		} finally {
//			this.lock.writeLock().unlock();
//		}
//	}

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

    @Override
	public ListenableFuture<QueryExecutionResults> processSparqlQuery(final Query sparqlQuery) {

		if(sparqlQuery.hasDatasetDescription() || sparqlQuery.toString(Syntax.syntaxSPARQL).contains("GRAPH")) {
			return processSparqlQueryWithGraphKeyword(sparqlQuery);
		}

		SettableFuture<QueryExecutionResults> resultFuture = SettableFuture.create();
		try {
			lockDataset();
			long start = System.currentTimeMillis();
			QueryResult result = queryEvaluator.getResult(sparqlQuery.toString(Syntax.syntaxSPARQL));
			long duration = System.currentTimeMillis() - start;
			LOG.info("Query Execution finished (duration: {} ms)", duration);
			ResultSet resultSet = toResultSet(result);
			resultFuture.set(new QueryExecutionResults(duration, resultSet));
		} catch (Exception ex) {
			LOG.error("Error while processing query:\n{}", sparqlQuery);
			resultFuture.setException(ex);
		} finally {
			unlockDataset();
		}

		return resultFuture;
	}


	private ListenableFuture<QueryExecutionResults> processSparqlQueryWithGraphKeyword(final Query sparqlQuery) {
		SettableFuture<QueryExecutionResults> resultFuture = SettableFuture.create();
		QueryExecution queryExecution = null;
		try {
			lockDataset();
			LOG.info("Start SPARQL query processing:\n{}", sparqlQuery.toString(Syntax.syntaxSPARQL));
			long start = System.currentTimeMillis();
			queryExecution = QueryExecutionFactory.create(sparqlQuery, dataset);

			ResultSet resultSet = queryExecution.execSelect();
			long duration = System.nanoTime() - start;
			resultFuture.set(new QueryExecutionResults(duration/1000000, ResultSetFactory.copyResults(resultSet)));
			LOG.info("SPARQL query successfully executed (duration: {} ns.)", duration);

			return resultFuture;
		} catch (Exception ex) {
			resultFuture.setException(ex);
			return resultFuture;
		} finally {
			if(queryExecution != null) {
				queryExecution.close();
			}
			unlockDataset();
		}
	}


	private void lockDataset(){
		this.lock.writeLock().lock();
	}

	private void unlockDataset(){
		this.lock.writeLock().unlock();
	}

	private class InferenceTask implements Runnable {
		@Override
		public void run() {
			try {
				lockDataset();
				long start = System.currentTimeMillis();
				InfModel infModel = ModelFactory.createInfModel(reasoner, dataset.getNamedModel("urn:x-arq:UnionGraph"));

				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				RDFDataMgr.write(outputStream, infModel, RDFFormat.TURTLE_FLAT);

				GeoFunctionRegisterer.registerGeoFunctions();
				queryEvaluator = new MemoryIndexQueryEvaluator();

				queryEvaluator.setupArguments();
				queryEvaluator.getArgs().set("result", lupos.datastructures.queryresult.QueryResult.TYPE.MEMORY);
				queryEvaluator.getArgs().set("codemap", LiteralFactory.MapType.TRIEMAP);
				queryEvaluator.getArgs().set("distinct", CommonCoreQueryEvaluator.DISTINCT.HASHSET);
				queryEvaluator.getArgs().set("join", CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
				queryEvaluator.getArgs().set("optional", CommonCoreQueryEvaluator.JOIN.HASHMAPINDEX);
				queryEvaluator.getArgs().set("datastructure", Indices.DATA_STRUCT.HASHMAP);

				queryEvaluator.init();

				Collection<URILiteral> uriLiterals = new LinkedList<>();
				uriLiterals.add(LiteralFactory.createStringURILiteral(
								"<inlinedata: " + new String(outputStream.toByteArray()) + ">")
				);

				queryEvaluator.prepareInputData(uriLiterals, new LinkedList<>());
				long duration = System.currentTimeMillis() - start;
				LOG.info("Inference task finished (duration: {} ms)", duration);

			} catch(Exception ex) {
				LOG.error("Error in inference task...", ex);
			} finally {
				unlockDataset();
			}
		}
	}
}

