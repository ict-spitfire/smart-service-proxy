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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
            RDFNode object = createRDFNode(o, model);
            ensurePrefixExists(prefixes, object);

            model.add(model.createStatement(subject, predicate, object));

        }

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
            RDFNode object;
            if(o.isURI() && !o.getURI().contains(":-")){
                object = model.createResource(o.getURI());
                ensurePrefixExists(prefixes, object);
            }
            else if (o.isLiteral() && !o.toString().contains(":-")){
                RDFDatatype datatype = o.getLiteralDatatype();
                if(datatype == null){
                    object = model.createLiteral(o.getLiteralLexicalForm());
                }
                else{
                    object = model.createTypedLiteral(o.getLiteralLexicalForm(), datatype);
                    ensurePrefixExists(prefixes, object);
                }
            }
            else {
                String key = o.toString().replace("\"", "");
                if(blanks.containsKey(key)){
                    object = blanks.get(key);
                }
                else {
                    object = model.createResource(AnonId.create(key));
                    blanks.put(key, (Resource) object);
                }
            }

            // parameter ?s
            Node s = binding.get(Var.alloc("s"));
            Resource subject;
            if(s.isURI() && !s.getURI().contains(":-")){
                subject = model.createResource(s.getURI());
                ensurePrefixExists(prefixes, subject);
            }
            else {
                String key = s.toString().replace("\"", "");
                if(blanks.containsKey(key)){
                    subject = blanks.get(key);
                }
                else {
                    subject = model.createResource(AnonId.create(key));
                    blanks.put(key, subject);
                }
            }

            subject.addProperty(predicate, object);
        }

        for(Map.Entry<String, String> entry : prefixes.entrySet()){
            model.setNsPrefix(entry.getValue(), entry.getKey());
        }

        return model;

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
