package eu.spitfire.ssp;

import eu.spitfire.ssp.server.handler.SemanticCache;
import eu.spitfire.ssp.server.handler.cache.HybridJenaTdbLuposdateSemanticCache;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
* Created by olli on 06.07.15.
*/
public class Main {

    private static Logger LOG = LoggerFactory.getLogger(Main.class.getName());

    private static Set<String> getFiles(String path) {
        Set<String> files = new HashSet<>();
        File directory = new File(path);
        if(!directory.exists()) {
            String message = "Configured directory from ssp.properties (" + path +") does not exists!";
            throw new IllegalArgumentException(message);
        } else if(!directory.isDirectory()) {
            String message = "Configured directory from ssp.properties (" + path +") is no directory!";
            throw new IllegalArgumentException(message);
        } else {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    files.addAll(getFiles(file.getAbsolutePath()));
                } else {
                    if (file.getAbsolutePath().endsWith("ttl") || file.getAbsolutePath().endsWith("rdf")) {
                        files.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return files;
    }

    public static void main(String[] args) throws Exception{

        System.out.println("MAX MEMORY: " + (Runtime.getRuntime().maxMemory() / 1000000) + "MB");

        Initializer initializer = new Initializer("ssp.properties") {

            @Override
            public SemanticCache createSemanticCache(Configuration config){

                String[] ontologyDirectories = config.getStringArray("cache.ontology.directory");
                Set<String> ontologyFiles = new HashSet<>();

                for(String directory : ontologyDirectories) {
                    ontologyFiles.addAll(getFiles(directory));
                }

                String tdbDirectory = config.getString("cache.tdb.directory");

                return new HybridJenaTdbLuposdateSemanticCache(
                    this.getIoExecutor(), this.getInternalTasksExecutor(), tdbDirectory, ontologyFiles
                );
            }
        };

        initializer.initialize();
    }
}
