package eu.spitfire.ssp.backends.slse;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import eu.spitfire.ssp.server.common.messages.WebserviceRegistrationMessage;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class SlseRegistry extends DataOriginRegistry<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected SlseRegistry(BackendComponentFactory<URI> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //Register Webservice for Virtual Sensor creation
        URI webserviceUri = new URI(null, null, null, -1, "/virtual-sensor-definition",null, null);
        HttpWebservice httpWebservice = new HttpVirtualSensorDefinitionWebservice(
                (SlseBackendComponentFactory) this.componentFactory);

        httpWebservice.setInternalTasksExecutorService(this.componentFactory.getInternalTasksExecutorService());
        httpWebservice.setIoExecutorService(this.componentFactory.getIoExecutorService());

        WebserviceRegistrationMessage registrationMessage = new WebserviceRegistrationMessage(webserviceUri,
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
