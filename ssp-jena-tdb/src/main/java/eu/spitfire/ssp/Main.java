package eu.spitfire.ssp;

import eu.spitfire.ssp.server.handler.SemanticCache;
import org.apache.commons.configuration.Configuration;

/**
 * Created by olli on 06.07.15.
 */
public class Main {

    public static void main(String[] args){
        Initializer initializer = new Initializer("ssp.properties") {

            @Override
            public SemanticCache createSemanticCache(Configuration config){
                return new JenaTdb
            }

        };

        initializer.initialize();
    }

}
