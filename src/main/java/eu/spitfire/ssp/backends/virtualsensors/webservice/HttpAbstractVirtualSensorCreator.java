package eu.spitfire.ssp.backends.virtualsensors.webservice;

import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backends.virtualsensors.VirtualSensor;
import eu.spitfire.ssp.backends.virtualsensors.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.backends.virtualsensors.VirtualSensorRegistry;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by olli on 30.06.14.
 */
public abstract class HttpAbstractVirtualSensorCreator extends HttpWebservice{

    private static Logger log = LoggerFactory.getLogger(HttpAbstractVirtualSensorCreator.class.getName());

    private VirtualSensorRegistry virtualSensorRegistry;
    private String graphNamePrefix;


    protected HttpAbstractVirtualSensorCreator(VirtualSensorBackendComponentFactory componentFactory,
                                               String graphNamePrefix, String htmlResourcePath){

        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), htmlResourcePath);
        this.virtualSensorRegistry = (VirtualSensorRegistry)componentFactory.getRegistry();
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
    protected URI getGraphName(String uriPath) throws URISyntaxException {
        return new URI(graphNamePrefix + uriPath);
    }


    protected ListenableFuture<Void> register(VirtualSensor dataOrigin) throws Exception{
        return this.virtualSensorRegistry.registerVirtualSensor(dataOrigin);
    }
}
