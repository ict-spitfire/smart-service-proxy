package eu.spitfire.ssp.utils;

import eu.spitfire.ssp.backend.vs.VirtualSensor;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.util.SplitIRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olli on 21.08.15.
 */
public class Converter {

    private static Logger LOG = LoggerFactory.getLogger(Converter.class.getName());


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
            model.setNsPrefix(entry.getValue(), entry.getKey());
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
            model.setNsPrefix(entry.getValue(), entry.getKey());
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
            }
            else{
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
            String namespace = SplitIRI.namespace(((Resource) node).getURI());
            if(!prefixes.containsKey(namespace)){
                if(VirtualSensor.RDF_NAMESPACE.equals(namespace)){
                    prefixes.put(namespace, "rdf");
                }
                else if(VirtualSensor.SSN_NAMESPACE.equals(namespace)){
                    prefixes.put(namespace, "ssn");
                }
                else {
                    prefixes.put(namespace, "ns" + (prefixes.keySet().size()));
                }
            }
        }
        else if(node.isLiteral() && ((Literal) node).getDatatype() != null
                && ((Literal) node).getDatatypeURI().startsWith(VirtualSensor.XSD_NAMESAPCE)){

            prefixes.put(VirtualSensor.XSD_NAMESAPCE, "xsd");
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
