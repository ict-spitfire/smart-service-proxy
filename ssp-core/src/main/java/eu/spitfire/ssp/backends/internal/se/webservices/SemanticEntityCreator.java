package eu.spitfire.ssp.backends.internal.se.webservices;

import eu.spitfire.ssp.backends.internal.se.SemanticEntityBackendComponentFactory;
import eu.spitfire.ssp.server.webservices.HttpWebservice;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntityCreator extends HttpWebservice {

    public SemanticEntityCreator(SemanticEntityBackendComponentFactory componentFactory, String htmlResourcePath) {

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);

    }



}
