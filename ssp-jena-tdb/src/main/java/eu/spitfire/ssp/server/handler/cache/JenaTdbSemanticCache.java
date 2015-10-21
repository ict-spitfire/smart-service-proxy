package eu.spitfire.ssp.server.handler.cache;

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
import eu.spitfire.ssp.server.internal.utils.Language;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringGraph;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.wrapper.QueryExecutionResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;


/**
* The Jena TDB Cache does *NOT* support GeoSPARQL but "Jena Spatial", i.e. a subset of the GeoSPARQL features with a
* different vocabulary!
*
* @author Oliver Kleine
*/
public class JenaTdbSemanticCache extends SemanticCache {

    private static Logger log = LoggerFactory.getLogger(JenaTdbSemanticCache.class.getName());

    private Dataset dataset;
	private Reasoner reasoner;


	public JenaTdbSemanticCache(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
				Path dbDirectory, String ontologyPath){

		super(ioExecutor, internalTasksExecutor);

        //Enable acceptence of literals having an unknown XSD datatype
        JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = true;

        //Disable acceptence of literals having an illegal value for the given XSD datatype
        JenaParameters.enableEagerLiteralValidation = true;

		File directory = dbDirectory.toFile();
		File[] oldFiles = directory.listFiles();

        assert oldFiles != null;
        for (File dbFile : oldFiles) {
            if(!dbFile.delete()){
                log.error("Could not delete old DB file: {}", dbFile);
            }
        }

		dataset = TDBFactory.createDataset(dbDirectory.toString());
		TDB.getContext().set(TDB.symUnionDefaultGraph, true);

		//Build the reasoner to infer new statements
		try {
			FileInputStream inputStream = new FileInputStream(ontologyPath);
			OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			ontologyModel.read(inputStream, "RDF/XML");
			inputStream.close();
			this.reasoner = ReasonerRegistry.getRDFSSimpleReasoner().bindSchema(ontologyModel);

		} catch (Exception ex) {
			log.error("Error while reading ontology...");
		}
	}


	@Override
	public ListenableFuture<ExpiringNamedGraph> getNamedGraph(URI graphName) {

        SettableFuture<ExpiringNamedGraph> resultFuture = SettableFuture.create();

        try {
            dataset.begin(ReadWrite.READ);

			if (graphName == null){
				log.error("Resource URI was NULL!");
                resultFuture.set(null);
                return resultFuture;
            }

			Model dbModel = dataset.getNamedModel(graphName.toString());

			if (dbModel.isEmpty()) {
				log.warn("No cached status found for resource {}", graphName);
				resultFuture.set(null);
			}

            else{
                log.info("Cached status found for resource {}", graphName);

				StringWriter writer = new StringWriter();
				dbModel.write(writer, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
				writer.flush();
				writer.close();

				ByteArrayInputStream inputStream = new ByteArrayInputStream(writer.toString().getBytes());
				Model outModel = ModelFactory.createDefaultModel();
				outModel.read(inputStream, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
				inputStream.close();

                resultFuture.set(new ExpiringNamedGraph(graphName, outModel, new Date()));
            }

            return resultFuture;
		}

        catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        }

        finally {
			dataset.end();
		}

	}

	@Override
	public ListenableFuture<ExpiringGraph> getDefaultGraph() {
		SettableFuture<ExpiringGraph> future = SettableFuture.create();
		future.setException(new RuntimeException("Not supported (default Graph)!"));
		return future;
	}

	@Override
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {

        SettableFuture<Boolean> resultFuture = SettableFuture.create();

        try{
            dataset.begin(ReadWrite.READ);
            Boolean result = !dataset.getNamedModel(graphName.toString()).isEmpty();
            resultFuture.set(result);
            return resultFuture;
        } catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
            dataset.end();
        }
    }


    @Override
	public ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph){

        SettableFuture<Void> resultFuture = SettableFuture.create();

        try {
            dataset.begin(ReadWrite.WRITE);

            long start = System.currentTimeMillis();

			InfModel infModel = ModelFactory.createInfModel(reasoner, namedGraph);
			dataset.replaceNamedModel(graphName.toString(), infModel);

			dataset.commit();
            log.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
			log.debug("Added status for resource {}", graphName);

            resultFuture.set(null);
            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			dataset.end();
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
            dataset.begin(ReadWrite.WRITE);
			dataset.removeNamedModel(graphName.toString());
			dataset.commit();
			log.debug("Removed status for resource {}", graphName);
            resultFuture.set(null);
            return resultFuture;
		} catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        } finally {
			dataset.end();
		}
	}

    @Override
	public ListenableFuture<QueryExecutionResults> processSparqlQuery(Query sparqlQuery) {

        SettableFuture<QueryExecutionResults> resultFuture = SettableFuture.create();
		dataset.begin(ReadWrite.READ);

		try {
			log.info("Start SPARQL query processing: {}", sparqlQuery.toString(Syntax.syntaxSPARQL));
			QueryExecution queryExecution = QueryExecutionFactory.create(sparqlQuery, dataset);

			try {
				long start = System.currentTimeMillis();
				ResultSet resultSet = queryExecution.execSelect();
				long duration = System.currentTimeMillis() - start;
                resultFuture.set(new QueryExecutionResults(duration, ResultSetFactory.copyResults(resultSet)));
				log.info("SPARQL query successfully executed!");
			} finally {
				queryExecution.close();
			}
            return resultFuture;
		} catch (Exception ex) {
			resultFuture.setException(ex);
            return resultFuture;
		} finally {
			dataset.end();
		}
	}
}

