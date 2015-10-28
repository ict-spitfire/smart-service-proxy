package eu.spitfire.ssp.server.internal.utils;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.impl.XSDBaseNumericType;
import com.hp.hpl.jena.vocabulary.XSD;
import eu.spitfire.ssp.backend.vs.VirtualSensor;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
//import com.hp.hpl.jena.util.SplitIRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 21.08.15.
 */
public class Converter {

    private static Logger LOG = LoggerFactory.getLogger(Converter.class.getName());

    /**
     * <a href="http://www.w3.org/2001/XMLSchema#">http://www.w3.org/2001/XMLSchema#</a>
     */
    public static final String XSD_NAMESAPCE = "http://www.w3.org/2001/XMLSchema#";

    /**
     * <a href="http://purl.oclc.org/NET/ssnx/ssn#">http://purl.oclc.org/NET/ssnx/ssn#</a>
     */
    public static final String SSN_NAMESPACE = "http://purl.oclc.org/NET/ssnx/ssn#";

    /**
     * <a href="http://www.w3.org/1999/02/22-rdf-syntax-ns#">http://www.w3.org/1999/02/22-rdf-syntax-ns#</a>
     */
    public static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /**
     * <a href="http://www.w3.org/2000/01/rdf-schema#">http://www.w3.org/2000/01/rdf-schema#</a>
     */
    public static final String RDFS_NAMESPACE = "http://www.w3.org/2000/01/rdf-schema#";

    /**
     * <a href="http://www.w3.org/2002/07/owl#">http://www.w3.org/2002/07/owl#</a>
     */
    public static final String OWL_NAMESPACE = "http://www.w3.org/2002/07/owl#";

    /**
     * <a href="http://www.geonames.org/ontology#">http://www.geonames.org/ontology#</a>
     */
    public static final String GEONAMES_NAMESPACE = "http://www.geonames.org/ontology#";

    /**
     * <a href="http://www.loa-cnr.it/ontologies/DUL.owl#">http://www.loa-cnr.it/ontologies/DUL.owl#</a>
     */
    public static final String DUL_NAMESPACE = "http://www.loa-cnr.it/ontologies/DUL.owl#";



    private static Map<String, String> RDF_PREFIXES = new HashMap<>();
    static{
        RDF_PREFIXES.put(XSD_NAMESAPCE, "xsd");
        RDF_PREFIXES.put(SSN_NAMESPACE, "ssn");
        RDF_PREFIXES.put(RDF_NAMESPACE, "rdf");
        RDF_PREFIXES.put(RDFS_NAMESPACE, "rdfs");
        RDF_PREFIXES.put(OWL_NAMESPACE, "owl");
        RDF_PREFIXES.put(GEONAMES_NAMESPACE, "gn");
        RDF_PREFIXES.put(DUL_NAMESPACE, "dul");
    }

    private static String getPrefix(String namespace){
        return RDF_PREFIXES.get(namespace);
    }

    public static Model toModel(ResultSet resultSet, String resource){

        if(resultSet == null){
            LOG.error("ResultSet was NULL!!!");
            return null;
        }

        Map<String, String> prefixes = new HashMap<>();
        Map<String, Resource> blanks = new HashMap<>();

        Model model = ModelFactory.createDefaultModel();
        Resource subject = model.createResource(resource);
        ensurePrefixExists(prefixes, subject);

        while(resultSet.hasNext()){
            Binding binding = resultSet.nextBinding();

            // parameter ?p
            Node p = binding.get(Var.alloc("p"));
            Property predicate =  ResourceFactory.createProperty(p.getURI());
            ensurePrefixExists(prefixes, predicate);

            // parameter ?o
            Node o = binding.get(Var.alloc("o"));
            RDFNode object = createObject(o, model, blanks, prefixes);

            subject.addProperty(predicate, object);

        }

        // set prefixes for better looking serialization
        for(Map.Entry<String, String> entry : prefixes.entrySet()){
            if(!model.getResource(entry.getKey()).listProperties().hasNext()) {
                model.setNsPrefix(entry.getValue(), entry.getKey());
            }
        }

        return model;
    }


    public static Model toModel(ResultSet resultSet){
        Model model = ModelFactory.createDefaultModel();

        if(resultSet == null){
            LOG.error("ResultSet was NULL!!!");
            return null;
        }

        Map<String, String> prefixes = new HashMap<>();
        Map<String, Resource> blanks = new HashMap<>();

        while(resultSet.hasNext()){
            Binding binding = resultSet.nextBinding();

            // parameter ?p
            Node p = binding.get(Var.alloc("p"));
            Property predicate =  model.createProperty(p.getURI());
            ensurePrefixExists(prefixes, predicate);

            // parameter ?o
            Node o = binding.get(Var.alloc("o"));
            RDFNode object = createObject(o, model, blanks, prefixes);

            // parameter ?s
            Node s = binding.get(Var.alloc("s"));
            Resource subject = createSubject(s, model, blanks, prefixes);
            subject.addProperty(predicate, object);


        }

        // set prefixes for better looking serialization
        for(Map.Entry<String, String> entry : prefixes.entrySet()){
            if(model.getResource(entry.getKey()) != null) {
                model.setNsPrefix(entry.getValue(), entry.getKey());
            }
        }

        return model;

    }

    private static RDFNode createObject(Node node, Model model, Map<String, Resource> blanks, Map<String, String> prefixes){
        RDFNode object;

        // URI resource (the construction of an actual URI is due to the lazy blank node support of LUPOSDATE)
        try{
            if(node.isURI() && new URI(node.getURI()).isAbsolute()){
                object = model.createResource(node.getURI());
                ensurePrefixExists(prefixes, object);
                return object;
            }
        }
        catch (Exception e) {
            LOG.debug("Doch keine URI...");
        }

        // Literal (contains(":-") is a hack for LUPOSDATE)
        if (node.isLiteral() && !node.toString().contains(":-")){
            RDFDatatype datatype = node.getLiteralDatatype();
            if(datatype == null){
                object = model.createLiteral(node.getLiteralLexicalForm());
            } else if (datatype.getURI().endsWith("#integer")) {
                object = model.createTypedLiteral(node.getLiteralLexicalForm(), XSDDatatype.XSDint);
                ensurePrefixExists(prefixes, object);
            } else {
                object = model.createTypedLiteral(node.getLiteralLexicalForm(), datatype);
                ensurePrefixExists(prefixes, object);
            }
            return object;
        }

        // Blank node (the replacement is also a hack for LUPOSDATE)
        String key = node.toString().replace("\"", "");
        if(blanks.containsKey(key)){
            return blanks.get(key);
        }

        object = model.createResource(AnonId.create(key));
        blanks.put(key, (Resource) object);
        return object;
    }


    private static Resource createSubject(Node node, Model model, Map<String, Resource> blanks, Map<String, String> prefixes){
        Resource subject;

        // URI resource (the construction of an actual URI is due to the lazy blank node support of LUPOSDATE)
        try{
            if(node.isURI() && new URI(node.getURI()).isAbsolute()){
                subject = model.createResource(node.getURI());
                ensurePrefixExists(prefixes, subject);
                return subject;
            }
        }
        catch (Exception e) {
            LOG.debug("Doch keine URI...");
        }

        // Blank node (the replacement is also a hack for LUPOSDATE)
        String key = node.toString().replace("\"", "");
        if(blanks.containsKey(key)){
            subject = blanks.get(key);
            return subject;
        }

        subject = model.createResource(AnonId.create(key));
        blanks.put(key, subject);
        return subject;
    }

    private static void ensurePrefixExists(Map<String, String> prefixes, RDFNode node){
        if(node.isURIResource() && !((Resource) node).getURI().contains(":-")){
            //String namespace = SplitIRI.namespace(((Resource) node).getURI());
            String namespace = getNamespace(((Resource) node).getURI());
            if(namespace != null && !prefixes.containsKey(namespace)){
                String prefix = getPrefix(namespace);
                if(prefix == null){
                    int number = 1;
                    for(String p : prefixes.values()){
                        if(p.startsWith("ns")){
                            number++;
                        }
                    }
                    prefix = "ns" + number;
                }
                prefixes.put(namespace, prefix);
            }
        }
        else if(node.isLiteral() && ((Literal) node).getDatatype() != null
                && ((Literal) node).getDatatypeURI().startsWith(XSD_NAMESAPCE)){

            prefixes.put(XSD_NAMESAPCE, "xsd");
        }
    }

    public static String getNamespace(String resourceName){
        try {
            URI resourceUri = new URI(resourceName);
            if(resourceUri.getRawFragment() != null){
                LOG.debug("Full  URI: {}", resourceName);
                LOG.debug("Namespace: {}", resourceUri.toString().substring(0, resourceUri.toString().lastIndexOf(resourceUri.getRawFragment())));
                return resourceUri.toString().substring(0, resourceUri.toString().lastIndexOf(resourceUri.getRawFragment()));
            } else if(resourceUri.getRawPath().length() > 1) {
                String tmp = resourceUri.toString();
                if(tmp.endsWith("/")){
                    return null;
                    //tmp = tmp.substring(0, tmp.length() - 2);
                }
                LOG.debug("Full  URI: {}", resourceName);
                LOG.debug("Namespace: {}", tmp.substring(0, tmp.lastIndexOf("/") + 1));
                return tmp.substring(0, tmp.lastIndexOf("/") + 1);
            } else {
                return null;
            }
        } catch(Exception ex){
            return null;
        }
    }

    private static RDFNode createRDFNode(Node object, Model model){
        if(!object.isURI() || object.toString().contains(":-"))
            return model.createResource(AnonId.create(object.toString().replace(":", "").replace("-", "")));

        if(!object.isLiteral())
            return model.createResource(object.getURI());

        RDFDatatype datatype = object.getLiteralDatatype();
        if(datatype == null){
            return model.createLiteral(object.getLiteralLexicalForm());
        }

        return model.createTypedLiteral(object.getLiteralLexicalForm(), datatype);
    }

}
