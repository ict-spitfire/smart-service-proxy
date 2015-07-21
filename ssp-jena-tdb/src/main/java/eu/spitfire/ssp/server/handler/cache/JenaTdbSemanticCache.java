package eu.spitfire.ssp.server.handler.cache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.shared.impl.JenaParameters;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.update.*;
import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringGraph;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.jena.query.spatial.EntityDefinition;
import org.apache.jena.query.spatial.SpatialDatasetFactory;
import org.apache.jena.query.spatial.SpatialQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;


/**
 * The Jena TDB Cache does *NOT* support GeoSPARQL but "Jena Spatial", i.e. a subset of the GeoSPARL features with a
 * different vocabulary!
 *
 * @author Oliver Kleine
*/
public class JenaTdbSemanticCache extends SemanticCache {

    private static Logger log = LoggerFactory.getLogger(JenaTdbSemanticCache.class.getName());

	private static final String SPT_SOURCE = "http://spitfire-project.eu/ontology.rdf";
	private static final String SPTSN_SOURCE = "http://spitfire-project.eu/sn.rdf";
	private static final String RULE_FILE_PROPERTY_KEY = "RULE_FILE";

	private static String ruleFile = null;

	private static OntModel ontologyBaseModel = null;



    private Dataset dataset;

	private Reasoner reasoner;


	public JenaTdbSemanticCache(ExecutorService ioExecutorService,
            ScheduledExecutorService internalTasksExecutorService, Path dbDirectory, Path spatialIndexDirectory){

		super(ioExecutorService, internalTasksExecutorService);

        //Disable acceptence of literals having an unknown XSD datatype
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


//        try{
//            EntityDefinition entityDefinition = new EntityDefinition("entityField", "geoField");
//            entityDefinition.setSpatialContextFactory(SpatialQuery.JTS_SPATIAL_CONTEXT_FACTORY_CLASS);
//
//            SpatialQuery.init();
//            Directory dir = FSDirectory.open(spatialIndexDirectory.toFile());
//            dataset = SpatialDatasetFactory.createLucene(tmpDataset, dir, entityDefinition);
//        }
//
//        catch (IOException e) {
//            log.error("This should never happen.", e);
//        }

		//Collect the SPITFIRE vocabularies
//		if (ontologyBaseModel == null) {
//			ontologyBaseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
//			if (isUriAccessible(SPT_SOURCE)) {
//				ontologyBaseModel.read(SPT_SOURCE, "RDF/XML");
//			}
//			if (isUriAccessible(SPTSN_SOURCE)) {
//				ontologyBaseModel.read(SPTSN_SOURCE, "RDF/XML");
//			}
//		}

//		reasoner = ReasonerRegistry.getRDFSSimpleReasoner().bindSchema(ontologyBaseModel);


//		if (ruleFile == null) {
//			ruleFile = getRuleFilePath();
//		}

//		Model m = ModelFactory.createDefaultModel();
//		Resource configuration = m.createResource();
//		configuration.addProperty(ReasonerVocabulary.PROPruleMode, "hybrid");
//		if (ruleFile != null) {
//			configuration.addProperty(ReasonerVocabulary.PROPruleSet, ruleFile);
//		}
//
//		reasoner= GenericRuleReasonerFactory.theInstance().create(configuration).bindSchema(ontologyBaseModel);


	}

	private static String getRuleFilePath() {
		String ret = null;
		Configuration config;
		try {
			config = new PropertiesConfiguration("ssp.properties");

			ret = config.getString(RULE_FILE_PROPERTY_KEY);

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}


		return ret;
	}

	private Reasoner getCustomReasoner() {
		return reasoner;
	}

	private static boolean isUriAccessible(String uri) {
		HttpURLConnection connection = null;
		int code = -1;
		URL myurl;
		try {
			myurl = new URL(uri);

			connection = (HttpURLConnection) myurl.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(1000);
			code = connection.getResponseCode();
		}
        catch (IOException e) {
			System.err.println(uri + " is not accessible.");
		}

		return (code == 200);
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

                Model model = ModelFactory.createDefaultModel();
                model.add(dbModel);

//                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, model, new Date());
                resultFuture.set(new ExpiringNamedGraph(graphName, model, new Date()));
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
    public ListenableFuture<Boolean> containsNamedGraph(URI graphName) {

        SettableFuture<Boolean> resultFuture = SettableFuture.create();

        try{
            dataset.begin(ReadWrite.READ);
            Boolean result = !dataset.getNamedModel(graphName.toString()).isEmpty();

            resultFuture.set(result);
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
	public ListenableFuture<Void> putNamedGraphToCache(URI graphName, Model namedGraph){

        SettableFuture<Void> resultFuture = SettableFuture.create();

        try {
            //dataset.begin(ReadWrite.WRITE);

//            long start = System.currentTimeMillis();
//
//			Model model = dataset.getNamedModel(graphName.toString());
//			model.removeAll();
////			model.add(ModelFactory.createInfModel(reasoner, namedGraph));
//            model.add(namedGraph);
////            InfModel im = ModelFactory.createInfModel(reasoner, resourceStatus);
////			InfModel im = ModelFactory.createInfModel(reasoner, resourceStatus);
//			// force starting the rule execution
////			im.prepare();
////			model.add(im);
//			dataset.commit();
//            log.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
//			log.debug("Added status for resource {}", graphName);

			putNamedGraphToCache2(graphName, namedGraph);

            resultFuture.set(null);
            return resultFuture;
		}

        catch(Exception ex){
			//dataset.end();
            resultFuture.setException(ex);
            return resultFuture;
        }

        finally {
			dataset.end();
		}
	}

	private void putNamedGraphToCache2(URI graphName, Model graph) throws Exception{

		long start = System.currentTimeMillis();

		//GraphStore graphStore = GraphStoreFactory.create(dataset) ;

		//delete old triples
		UpdateRequest request = UpdateFactory.create();
		request.add(createDeleteQuery(graphName));
		request.add(createInsertQuery(graphName, graph));

		try {
			dataset.begin(ReadWrite.WRITE);
			UpdateAction.execute(request, dataset);
			//dataset.commit();
		}
		finally {
			dataset.end();
		}

		log.info("Deleted old and inserted new graph \"{}\" ({} ms)", graphName, System.currentTimeMillis() - start);
	}

	private static String createDeleteQuery(URI graphName){
		return "DELETE { " + "GRAPH <" + graphName + "> { ?s ?p ?o }" + " ?s ?p ?o } WHERE { ?s ?p ?o }";
	}

	private static String createInsertQuery(URI graphName, Model graph){

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

		return "INSERT DATA { GRAPH <" + graphName + "> { " + triples.toString() + " } " + triples.toString() + " } ";

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
		}

        catch(Exception ex){
            resultFuture.setException(ex);
            return resultFuture;
        }

        finally {
			dataset.end();
		}
	}

//	@Override
//	public void updateStatement(Statement statement) throws Exception {
//
//		dataset.begin(ReadWrite.WRITE);
//		try {
//			Model tdbModel = dataset.getNamedModel(statement.getSubject().toString());
//
//			Statement oldStatement = tdbModel.getProperty(statement.getSubject(), statement.getPredicate());
//			Statement updatedStatement;
//			if (oldStatement != null) {
//				if ("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())) {
//					RDFNode object =
//							tdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
//					updatedStatement = oldStatement.changeObject(object);
//					dataset.commit();
//
//				} else {
//					updatedStatement = oldStatement.changeObject(statement.getObject());
//					dataset.commit();
//				}
//				log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
//						updatedStatement.getSubject(), updatedStatement.getObject()});
//			} else
//				log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
//						statement.getPredicate());
//		} finally {
//			dataset.end();
//		}
//
//	}

    @Override
	public ListenableFuture<ResultSet> processSparqlQuery(Query sparqlQuery) {

        SettableFuture<ResultSet> resultFuture = SettableFuture.create();
		dataset.begin(ReadWrite.READ);

		try {
			log.info("Start SPARQL query processing: {}", sparqlQuery.toString(Syntax.syntaxSPARQL));

			QueryExecution queryExecution = QueryExecutionFactory.create(sparqlQuery, dataset);

//            Model resultGraph = ModelFactory.createDefaultModel();

//			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
                resultFuture.set(queryExecution.execSelect());
				log.info("SPARQL query successfully executed!");
//                while(resultSet.hasNext()){
//                    QuerySolution querySolution = resultSet.next();
//                    Statement statement = resultGraph.createStatement(
//                            querySolution.getResource("?s"),
//                            resultGraph.createProperty(querySolution.getResource("?p").getURI()),
//                            querySolution.get("?o")
//                    );
//                    resultGraph.add(statement);
//                }
//                resultSet.ne
//                ResultSetFormatter.asRDF(resultGraph, resultSet);

//				ResultSetFormatter.outputAsXML(baos, resultSet);
//
//                log.info("SPARQL query result: {}", baos.toString());

			} finally {
				queryExecution.close();
			}
//			String result = baos.toString("UTF-8");
//            ExpiringGraph expiringGraph = new ExpiringGraph(resultGraph, new Date());
//			resultFuture.set(new ExpiringGraphHttpResponse(HttpResponseStatus.OK, expiringGraph));

            return resultFuture;
		}

        catch (Exception ex) {
			resultFuture.setException(ex);
            return resultFuture;
		}

        finally {
			dataset.end();
		}
	}

//
//	@Override
//	public boolean supportsSPARQL() {
//		return true;
//	}
}

