package eu.spitfire.ssp.backends.internal.se;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Registry;
import eu.spitfire.ssp.server.common.messages.WebserviceRegistrationMessage;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntityRegistry extends Registry<URI, SemanticEntity>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    protected SemanticEntityRegistry(BackendComponentFactory<URI, SemanticEntity> componentFactory) {
        super(componentFactory);
    }


    @Override
    public void startRegistry() throws Exception {
        //TODO register SE creation services
    }

    private void registerWebservice(HttpWebservice httpWebservice, URI webserviceUri){

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
