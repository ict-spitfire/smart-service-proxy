package eu.spitfire.ssp;

import eu.spitfire.ssp.server.handler.cache.LuposdateSemanticCache;
import eu.spitfire.ssp.server.handler.cache.SemanticCache;
import org.apache.commons.configuration.Configuration;

/**
 * Created by olli on 29.07.14.
 */
public class Main {


    public static void main(String[] args) throws Exception {

        Initializer initializer = new Initializer("ssp.properties") {

            @Override
            public SemanticCache createSemanticCache(Configuration config){
                return new LuposdateSemanticCache(this.getIoExecutor(), this.getInternalTasksExecutor());
            }

        };

        initializer.initialize();
    }

}
