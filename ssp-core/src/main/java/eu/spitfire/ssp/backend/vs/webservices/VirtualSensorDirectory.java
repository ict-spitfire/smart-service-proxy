package eu.spitfire.ssp.backend.vs.webservices;

import eu.spitfire.ssp.backend.vs.VirtualSensorsBackendComponentFactory;
import eu.spitfire.ssp.server.webservices.HttpWebservice;

/**
 * Created by olli on 18.06.15.
 */
public class VirtualSensorDirectory extends HttpWebservice {

    public VirtualSensorDirectory(VirtualSensorsBackendComponentFactory componentFactory) {
        super(
                componentFactory.getIoExecutor(),
                componentFactory.getInternalTasksExecutor(),
                "html/services/virtual-sensor-directory.html"
        );
    }
}
