package eu.spitfire.ssp.backends.internal.vs.webservices;

import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorRegistry;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by olli on 30.06.14.
 */
public abstract class AbstractVirtualSensorCreator extends HttpWebservice{

    private static Logger log = LoggerFactory.getLogger(AbstractVirtualSensorCreator.class.getName());

    private VirtualSensorRegistry virtualSensorRegistry;
    private String graphNamePrefix;


    protected AbstractVirtualSensorCreator(VirtualSensorBackendComponentFactory componentFactory,
                                           String graphNamePrefix, String htmlResourcePath){

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);
        this.virtualSensorRegistry = componentFactory.getRegistry();
        this.graphNamePrefix = graphNamePrefix;
    }


    /**
     * Returns the fully qualified name of a graph with the given path
     *
     * @param uriPath the path of the graph name
     *
     * @return the fully qualified name of a graph with the given path
     *
     * @throws URISyntaxException
     */
    protected URI createGraphName(String uriPath) throws URISyntaxException {
        return new URI(graphNamePrefix + uriPath);
    }

    protected VirtualSensorRegistry getVirtualSensorRegistry(){
        return this.virtualSensorRegistry;
    }
}
