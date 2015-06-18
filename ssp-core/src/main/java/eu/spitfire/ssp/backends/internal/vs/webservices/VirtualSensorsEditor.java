package eu.spitfire.ssp.backends.internal.vs.webservices;

import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.server.webservices.HttpWebservice;

/**
 * Created by olli on 18.06.15.
 */
public class VirtualSensorsEditor extends HttpWebservice {

    public VirtualSensorsEditor(VirtualSensorBackendComponentFactory componentFactory) {
        super(
                componentFactory.getIoExecutor(),
                componentFactory.getInternalTasksExecutor(),
                "html/semantic-entities/virtual-sensors-editor.html"
        );
    }
}
