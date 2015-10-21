package eu.spitfire.ssp;

import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.handler.cache.JenaTdbSemanticCache;
import org.apache.commons.configuration.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
* Created by olli on 06.07.15.
*/
public class Main {

    public static void main(String[] args) throws Exception{
        Initializer initializer = new Initializer("ssp.properties") {

            @Override
            public SemanticCache createSemanticCache(Configuration config){
                Path tdb = Paths.get("/home", "olli", "jena-tdb");
                Path index = Paths.get("/home", "olli", "jena-tdb-index");

                String ontologyPath = config.getString("cache.ontology.path");
                System.out.println("Using Ontology at: " + ontologyPath);
                return new JenaTdbSemanticCache(this.getIoExecutor(), this.getInternalTasksExecutor(), tdb, ontologyPath);
            }

        };

        initializer.initialize();
    }

}
