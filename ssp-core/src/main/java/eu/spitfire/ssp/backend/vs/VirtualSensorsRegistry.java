package eu.spitfire.ssp.backend.vs;

import eu.spitfire.ssp.backend.generic.BackendComponentFactory;
import eu.spitfire.ssp.backend.generic.DataOriginRegistry;
import eu.spitfire.ssp.backend.vs.webservices.VirtualSensorBatchCreator;
import eu.spitfire.ssp.backend.vs.webservices.VirtualSensorCreator;
import eu.spitfire.ssp.backend.vs.webservices.VirtualSensorDirectory;
import eu.spitfire.ssp.server.internal.message.WebserviceRegistration;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensorsRegistry extends DataOriginRegistry<URI, VirtualSensor> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected VirtualSensorsRegistry(BackendComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //Register Webservice for Virtual Sensor creation
        this.registerWebservice(
                new VirtualSensorCreator((VirtualSensorsBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/virtual-sensor-creation", null, null)
        );

        this.registerWebservice(
                new VirtualSensorBatchCreator((VirtualSensorsBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/virtual-sensor-batch-creation", null, null)
        );

        this.registerWebservice(
                new VirtualSensorDirectory((VirtualSensorsBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/virtual-sensor-directory", null, null)
        );

    }


    private void registerWebservice(HttpWebservice httpWebservice, URI webserviceUri){

        WebserviceRegistration registrationMessage = new WebserviceRegistration(webserviceUri,
                httpWebservice);

        ChannelFuture future = Channels.write(this.componentFactory.getLocalChannel(), registrationMessage);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    log.info("Succesfully registered Webservice to create virtual sensors!");
                else
                    log.error("Could not register Webservice to create virtual sensors!", future.getCause());
            }
        });
    }
}
