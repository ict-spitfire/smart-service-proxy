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
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
* The Jena TDB Cache does *NOT* support GeoSPARQL but "Jena Spatial", i.e. a subset of the GeoSPARQL features with a
* different vocabulary!
*
* @author Oliver Kleine
*/
public class JenaTdbSemanticCache extends SemanticCache {

    private static Logger LOG = LoggerFactory.getLogger(JenaTdbSemanticCache.class.getName());

    private Dataset dataset;
	private Reasoner reasoner;

	private ReentrantReadWriteLock lock;

	public JenaTdbSemanticCache(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
				String tdbDirectory, Set<String> ontologyPaths){

		super(ioExecutor, internalTasksExecutor);

		this.lock = new ReentrantReadWriteLock();

        //Enable acceptence of literals having an unknown XSD datatype
        JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = true;

        //Disable acceptence of literals having an illegal value for the given XSD datatype
        JenaParameters.enableEagerLiteralValidation = true;

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
	}


	@Override
	public ListenableFuture<ExpiringNamedGraph> getNamedGraph(URI graphName) {

        SettableFuture<ExpiringNamedGraph> resultFuture = SettableFuture.create();

        try {
            //dataset.begin(ReadWrite.READ);
			this.lock.readLock().lock();

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
//				StringWriter writer = new StringWriter();
				RDFDataMgr.write(outputStream, dbModel, RDFFormat.TURTLE);
				//dbModel.write(writer, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
//				writer.flush();
//				writer.close();

				//LOG.error(writer.toString());

				ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
				Model outModel = ModelFactory.createDefaultModel();
				RDFDataMgr.read(outModel, inputStream, RDFFormat.TURTLE.getLang());
				//outModel.read(inputStream, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
				inputStream.close();

                resultFuture.set(new ExpiringNamedGraph(graphName, outModel, new Date()));
            }

            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			this.lock.readLock().unlock();
			//dataset.end();
		}

	}

	@Override
	public ListenableFuture<ExpiringGraph> getDefaultGraph() {
		SettableFuture<ExpiringGraph> future = SettableFuture.create();
	    try{
	    	this.lock.readLock().lock();
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
			this.lock.readLock().unlock();
		}

		return future;
	}

	@Override
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {

        SettableFuture<Boolean> resultFuture = SettableFuture.create();

        try{
			this.lock.readLock().lock();
            //dataset.begin(ReadWrite.READ);
            Boolean result = !dataset.getNamedModel(graphName.toString()).isEmpty();
            resultFuture.set(result);
            return resultFuture;
        } catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			this.lock.readLock().unlock();
            //dataset.end();
        }
    }


    @Override
	public ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph){

        SettableFuture<Void> resultFuture = SettableFuture.create();

        try {
			this.lock.writeLock().lock();
            ////dataset.begin(ReadWrite.WRITE);

            long start = System.currentTimeMillis();

			//InfModel infModel = ModelFactory.createInfModel(reasoner, namedGraph);
			dataset.removeNamedModel(graphName.toString());
			LOG.info("Removed old graph \"{}\" (duration: {} ms)", graphName, System.currentTimeMillis() - start);
			dataset.addNamedModel(graphName.toString(), namedGraph);
			//dataset.getNamedModel(graphName.toString()).removeAll().add(namedGraph);
			LOG.info("Added new graph \"{}\" (duration: {} ms)", graphName, System.currentTimeMillis() - start);
			//dataset.commit();
            LOG.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
			LOG.debug("Added status for resource {}", graphName);

            resultFuture.set(null);
            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			this.lock.writeLock().unlock();
			////dataset.end();
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

	@Override
	protected ScheduledExecutorService getCacheTasksExecutor() {
		return this.getInternalTasksExecutor();
	}

	@Override
	public ListenableFuture<QueryExecutionResults> processSparqlQuery(Query sparqlQuery) {

        SettableFuture<QueryExecutionResults> resultFuture = SettableFuture.create();
		//dataset.begin(ReadWrite.READ);

		try {
			this.lock.readLock().lock();
			String queryString = sparqlQuery.toString(Syntax.syntaxSPARQL);
			LOG.info("Start SPARQL query processing:\n{}", queryString);

			long start;
			QueryExecution queryExecution;
			if(!sparqlQuery.hasDatasetDescription() && !queryString.contains("GRAPH")) {
			    long duration;
				start = System.nanoTime();
				InfModel model = ModelFactory.createInfModel(reasoner, dataset.getNamedModel("urn:x-arq:UnionGraph"));
				duration = System.nanoTime() - start;
				LOG.info("Inference completed (duration: {} ns.)", duration);
//				start = System.nanoTime();
				queryExecution = QueryExecutionFactory.create(sparqlQuery, model);

			} else {
				start = System.nanoTime();
				queryExecution = QueryExecutionFactory.create(sparqlQuery, dataset);
			}

			try {
				ResultSet resultSet = queryExecution.execSelect();
				long duration = System.nanoTime() - start;
                resultFuture.set(new QueryExecutionResults(duration/1000000, ResultSetFactory.copyResults(resultSet)));
				LOG.info("SPARQL query successfully executed (duration: {} ns.)", duration);
			} finally {
				queryExecution.close();
			}
            return resultFuture;
		} catch (Exception ex) {
			resultFuture.setException(ex);
            return resultFuture;
		} finally {
			this.lock.readLock().unlock();
			//dataset.end();
		}
	}
}

