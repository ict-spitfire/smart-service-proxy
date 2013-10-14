package eu.spitfire.ssp.server.channels.handler.cache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 05.09.13
 * Time: 09:43
 * To change this template use File | Settings | File Templates.
 */
public class JenaTdbSemanticCache extends SemanticCache {

	private static final String SPT_SOURCE = "http://spitfire-project.eu/ontology.rdf";
	private static final String SPT_NS = "http://spitfire-project.eu/ontology/ns/";
	private static final String SPTSN_SOURCE = "http://spitfire-project.eu/sn.rdf";

	private static OntModel ontologyBaseModel = null;

	private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	private Dataset dataset;


	public JenaTdbSemanticCache(ScheduledExecutorService scheduledExecutorService, Path dbDirectory) {
		super(scheduledExecutorService);

		File fin = dbDirectory.toFile();
		File[] filesInList = fin.listFiles();
		for (int n = 0; n < filesInList.length; n++) {
			if (filesInList[n].isFile()) {
				System.gc();
				filesInList[n].delete();
			}
		}

		dataset = TDBFactory.createDataset(dbDirectory.toString());
		TDB.getContext().set(TDB.symUnionDefaultGraph, true);

		//Collect the SPITFIRE vocabularies
		if (ontologyBaseModel == null) {
			ontologyBaseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			if (isUriAccessible(SPT_SOURCE)) {
				ontologyBaseModel.read(SPT_SOURCE, "RDF/XML");
			}
			if (isUriAccessible(SPTSN_SOURCE)) {
				ontologyBaseModel.read(SPTSN_SOURCE, "RDF/XML");
			}
		}
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
		} catch (MalformedURLException e) {
			System.err.println(uri + " is not accessible.");
		} catch (ProtocolException e) {
			System.err.println(uri + " is not accessible.");
		} catch (IOException e) {
			System.err.println(uri + " is not accessible.");
		}
		return (code == 200) ? true : false;
	}


	@Override
	public InternalResourceStatusMessage getCachedResource(URI resourceUri) throws Exception {
		dataset.begin(ReadWrite.READ);
		try {
			Model model = dataset.getNamedModel(resourceUri.toString());

			if (model.isEmpty()) {
				log.warn("No cached status found for resource {}", resourceUri);
				return null;
			}
			log.info("Cached status found for resource {}", resourceUri);
			return new InternalResourceStatusMessage(model, new Date());
		} catch (NullPointerException npe) {
			return new InternalResourceStatusMessage(ModelFactory.createDefaultModel(), new Date());
		} finally {
			dataset.end();
		}

	}

	@Override
	public void putResourceToCache(final URI resourceUri, final Model resourceStatus) throws Exception {
		deleteResource(resourceUri);

//		Model owlFullModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
//		owlFullModel.add(ontologyBaseModel);
//		owlFullModel.add(resourceStatus);
//		dataset.addNamedModel(resourceUri.toString(), resourceStatus);

		dataset.begin(ReadWrite.WRITE);
		try {
//			dataset.addNamedModel(SPT_NS, owlFullModel);
			dataset.addNamedModel(resourceUri.toString(), resourceStatus);
			dataset.commit();
			log.debug("Added status for resource {}", resourceUri);
		} finally {
			dataset.end();
		}
	}

	@Override
	public void deleteResource(URI resourceUri) throws Exception {
		dataset.begin(ReadWrite.WRITE);
		try {
			dataset.removeNamedModel(resourceUri.toString());
			dataset.commit();
			log.debug("Removed status for resource {}", resourceUri);
		} finally {
			dataset.end();
		}
	}

	@Override
	public void updateStatement(Statement statement) throws Exception {

		dataset.begin(ReadWrite.WRITE);
		try {
			Model tdbModel = dataset.getNamedModel(statement.getSubject().toString());

			Statement oldStatement = tdbModel.getProperty(statement.getSubject(), statement.getPredicate());
			Statement updatedStatement;
			if (oldStatement != null) {
				if ("http://spitfire-project.eu/ontology/ns/value".equals(oldStatement.getPredicate().toString())) {
					RDFNode object =
							tdbModel.createTypedLiteral(statement.getObject().asLiteral().getFloat(), XSDDatatype.XSDfloat);
					updatedStatement = oldStatement.changeObject(object);
					dataset.commit();

				} else {
					updatedStatement = oldStatement.changeObject(statement.getObject());
					dataset.commit();
				}
				log.info("Updated property {} of resource {} to {}", new Object[]{updatedStatement.getPredicate(),
						updatedStatement.getSubject(), updatedStatement.getObject()});
			} else
				log.warn("Resource {} not (yet?) found. Could not update property {}.", statement.getSubject(),
						statement.getPredicate());
		} finally {
			dataset.end();
		}

	}

	public synchronized void processSparqlQuery(SettableFuture<String> queryResultFuture, String sparqlQuery) {

		dataset.begin(ReadWrite.READ);
		try {
			log.info("Start SPARQL query processing: {}", sparqlQuery);

			Query query = QueryFactory.create(sparqlQuery);
			QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ResultSet resultSet = queryExecution.execSelect();
				ResultSetFormatter.outputAsXML(baos, resultSet);
			} finally {
				queryExecution.close();
			}
			String result = baos.toString("UTF-8");

			queryResultFuture.set(result);
		} catch (Exception e) {
			queryResultFuture.setException(e);
		} finally {
			dataset.end();
		}
	}

	@Override
	public boolean supportsSPARQL() {
		return true;
	}
}

