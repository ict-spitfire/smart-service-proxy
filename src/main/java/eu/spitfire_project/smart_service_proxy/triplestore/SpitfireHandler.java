package eu.spitfire_project.smart_service_proxy.triplestore;

//import de.rwglab.indexer.conn.triplestore.AbstractTripleStoreConnector;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.javatuples.Quartet;
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.turtle.TurtleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;

    public class SpitfireHandler implements Runnable, RDFHandler
    {
        private static final Logger log = LoggerFactory.getLogger(SpitfireHandler.class);

        //private SpitfireRestClient client = new SpitfireRestClient();

        private String context = null;
        private TurtleParser parser;
        HashMap<String, String> ns = new HashMap<String, String>();

        private LinkedList<Quartet<String, String, String, String>> triples = new LinkedList<Quartet<String, String, String, String>>();

        private URI sensorURI;
		private String rdf;
        private AbstractTripleStoreConnector tsc = null;


		public SpitfireHandler(URI u, String rdfxml){
            Model model = ModelFactory.createDefaultModel();
            model.read(new ByteArrayInputStream(rdfxml.getBytes(Charset.forName("UTF-8"))),
                       null, "RDF/XML");
            StringWriter writer = new StringWriter();

            model.write(writer, "N3");

            rdf = writer.toString();
            log.debug("N3 content:\n" + rdf);
		}

        public SpitfireHandler(URI u)
        {
            parser = new TurtleParser();
            parser.setRDFHandler(this);
            this.sensorURI = u;
        }

        public void run()
        {
            try
            {
                tsc = AbstractTripleStoreConnector.getInstance();
                tsc.connect();
                tsc.setAutoCommit(false);
                tsc.clear(sensorURI.toString());

                log.info("Indexer for " + sensorURI + " started.");
                //String rdf = client.getRDF(sensorURI);

                if (rdf != null)
                {
                    try
                    {
                        parser.parse(new StringReader(rdf), "");
                    } catch (RDFParseException e)
                    {
                        log.info("Parse error for rdf from: " + sensorURI.toASCIIString());
                    } catch (RDFHandlerException e)
                    {
                        log.info("Problem with RDFHandler");
                    } catch (IOException e)
                    {
                        log.info("Error while reading rdf from: " + sensorURI.toASCIIString());
                    }
                }
                for (Quartet<String, String, String, String> t : triples)
                {
                    processRDF(t.getValue0(), t.getValue1(), t.getValue2(), t.getValue3());
                }
                tsc.commit();
                tsc.close();
                tsc.disconnect();
                log.info("Indexer for " + sensorURI + " finished.");
            } catch (Exception e)
            {
                log.warn("Could not run spitfire handler due to a triplestore connection problems");
            }
        }


        private void processRDF(String s, String p, String o, String c)
        {
            addAdditionalRDF(s, p, o, c);
            deleteOldRDF(s, p, o, c);
            addNewRDF(s, p, o, c);
        }

        private void addAdditionalRDF(String s, String p, String o, String context)
        {

        }

        private void deleteOldRDF(String s, String p, String o, String c)
        {
            tsc.deleteTriple(null, "http://spitfire-project.eu/ontology/ns/value", null, sensorURI.toString().replace("/light/_minimal", "/rdf"));
        }

        private void addNewRDF(String s, String p, String o, String c)
        {
            tsc.insertTriple(s, p, o, sensorURI.toString());
        }

        public void endRDF() throws RDFHandlerException
        {
        }

        public void handleComment(String arg0) throws RDFHandlerException
        {
        }

        public void handleNamespace(String prefix, String url)
                throws RDFHandlerException
        {
            ns.put(prefix + ":", url);
        }

        public void handleStatement(Statement s) throws RDFHandlerException
        {
            String sub = resolveNS(s.getSubject().toString());
            String pred = resolveNS(s.getPredicate().toString());
            String obj = resolveNS(s.getObject().toString());
            //String c = resolveNS(s.getContext().toString());
            String c = null;

            triples.add(Quartet.with(sub, pred, obj, c));
        }

        private String resolveNS(String s)
        {
            for (String prefix : ns.keySet())
            {
                if (s.contains(prefix))
                {
                    return s.replace(prefix, ns.get(prefix));
                }
            }
            return s;
        }

        public void startRDF() throws RDFHandlerException
        {
            ns.clear();
        }

        public void setContext(String context)
        {
            this.context = context;
        }

    }
