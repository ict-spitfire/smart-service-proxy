package eu.spitfire.ssp.backends.internal.se;

import eu.spitfire.ssp.CmdLineArguments;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.Observer;
import eu.spitfire.ssp.backends.internal.se.webservices.SemanticEntitiesEditor;
import eu.spitfire.ssp.server.internal.messages.requests.WebserviceRegistration;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntityBackendComponentFactory extends BackendComponentFactory<URI, SemanticEntity>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private SemanticEntityAccessor semanticEntityAccessor;


    public SemanticEntityBackendComponentFactory(Configuration config, LocalServerChannel localChannel,
                                                    ScheduledExecutorService internalTasksExecutor, ExecutorService ioExecutor)
            throws Exception {

        super("se", config, localChannel, internalTasksExecutor, ioExecutor);
    }


    @Override
    public void initialize() throws Exception {
        this.semanticEntityAccessor = new SemanticEntityAccessor(this);

        registerWebservice(
            new SemanticEntitiesEditor(this.getIoExecutor(), this.getInternalTasksExecutor()),
            new URI(null, null, null, -1, "/services/semantic-entities/semantic-entities-editor", null, null)
        );
    }


    private void registerWebservice(HttpWebservice httpWebservice, URI webserviceUri){

        WebserviceRegistration registrationMessage = new WebserviceRegistration(webserviceUri,
                httpWebservice);

        ChannelFuture future = Channels.write(this.getLocalChannel(), registrationMessage);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess())
                    log.info("Successfully registered Webservice to edit semantic entities!");
                else
                    log.error("Could not register Webservice to edit semantic entities!", future.getCause());
            }
        });
    }

    /**
     * Returns <code>null</code> since instances of {@link eu.spitfire.ssp.backends.internal.se.SemanticEntity} are not
     * observable.
     *
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to be observed
     *
     * @return <code>null</code> since instances of {@link eu.spitfire.ssp.backends.internal.se.SemanticEntity} are not
     * observable.
     */
    @Override
    public Observer<URI, SemanticEntity> getObserver(SemanticEntity semanticEntity) {
        return null;
    }


    @Override
    public SemanticEntityAccessor getAccessor(SemanticEntity dataOrigin) {
        return this.semanticEntityAccessor;
    }


    @Override
    public SemanticEntityRegistry createRegistry(Configuration config) throws Exception {
        return new SemanticEntityRegistry(this);
    }

    @Override
    public SemanticEntityRegistry getRegistry(){
        return (SemanticEntityRegistry) super.getRegistry();
    }


    @Override
    public void shutdown() {

    }
}
