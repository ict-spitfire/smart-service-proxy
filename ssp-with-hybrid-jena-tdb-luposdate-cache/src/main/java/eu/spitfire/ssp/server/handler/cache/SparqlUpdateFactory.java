//package eu.spitfire.ssp.server.handler.cache;
//
//import com.hp.hpl.jena.rdf.model.Model;
//
//import java.net.URI;
//import java.util.Locale;
//import java.util.Set;
//
///**
// * Created by olli on 30.11.15.
// */
//public class SparqlUpdateFactory {
//
//
//    private static final String DELETE_QUERY_TEMPLATE =
//            "DELETE {\n\t" +
//                "GRAPH <%s> { ?s ?p ?o }\n\t" +
//                "?s ?p ?o\n" +
//            "} WHERE {\n\t" +
//                "GRAPH <%s> {?s ?p ?o }\n" +
//            "}";
//
//    private static String createDeleteQuery(URI graphName){
//        String query = String.format(Locale.ENGLISH, DELETE_QUERY_TEMPLATE, graphName, graphName);
//        LOG.debug("\n" + query);
//        return query;
//    }
//
//
//    private static final String INSERT_QUERY_TEMPLATE =
//            "INSERT DATA {\n\t" +
//                    "GRAPH <%s> {" +
//                    "%s\n" +
//                    "}" +
//                    "%s\n" +
//                    "}";
//
//    private static String createInsertQuery(URI graphName, Set<String> statements){
//        String triples = "";
//        for(String triple : statements){
//            triples += triple;
//        }
//
//        String query = String.format(Locale.ENGLISH, INSERT_QUERY_TEMPLATE, graphName.toString(), "\t" + triples, triples);
//        LOG.debug("\n" + query);
//        return query;
//    }
//}
