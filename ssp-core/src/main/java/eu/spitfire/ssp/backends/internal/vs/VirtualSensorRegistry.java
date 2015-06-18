package eu.spitfire.ssp.backends.internal.vs;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Registry;
import eu.spitfire.ssp.backends.internal.vs.webservices.VirtualSensorBatchCreator;
import eu.spitfire.ssp.backends.internal.vs.webservices.VirtualSensorCreator;
import eu.spitfire.ssp.backends.internal.vs.webservices.VirtualSensorsEditor;
import eu.spitfire.ssp.server.internal.messages.requests.WebserviceRegistration;
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
public class VirtualSensorRegistry extends Registry<URI, VirtualSensor> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected VirtualSensorRegistry(BackendComponentFactory<URI, VirtualSensor> componentFactory) {
        super(componentFactory);
    }


//    public ListenableFuture<Void> registerVirtualSensor(VirtualSensor dataOrigin){
//        return this.registerDataOrigin(dataOrigin);
//    }


    @Override
    public void startRegistry() throws Exception {
        //Register Webservice for Virtual Sensor creation
        this.registerWebservice(
                new VirtualSensorCreator((VirtualSensorBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/semantic-entities/virtual-sensor-creation", null, null)
        );

        this.registerWebservice(
                new VirtualSensorBatchCreator((VirtualSensorBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/semantic-entities/virtual-sensor-batch-creation", null, null)
        );

        this.registerWebservice(
                new VirtualSensorsEditor((VirtualSensorBackendComponentFactory) this.componentFactory),
                new URI(null, null, null, -1, "/services/semantic-entities/virtual-sensors-editor", null, null)
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
