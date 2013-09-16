package eu.spitfire.ssp.backends.coap.noderegistration;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import eu.spitfire.ssp.backends.ProxyServiceException;
import eu.spitfire.ssp.backends.coap.CoapBackendManager;
import eu.spitfire.ssp.backends.coap.ResourceToolBox;
import eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import eu.spitfire.ssp.server.pipeline.messages.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.09.13
 * Time: 16:21
 * To change this template use File | Settings | File Templates.
 */
public class CoapResponseProcessorToRegisterResources implements CoapResponseProcessor, RetransmissionTimeoutProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapBackendManager coapGatewayManager;
    private URI serviceUri;
    private HttpRequestProcessorForCoapServices httpRequestProcessor;
    private ListeningExecutorService executorService;
    private LocalServerChannel localChannel;
    private SettableFuture<Boolean> nodeRegistrationFuture;

    public CoapResponseProcessorToRegisterResources(CoapBackendManager coapGatewayManager, URI serviceUri,
                                                    HttpRequestProcessorForCoapServices httpRequestProcessor,
                                                    ListeningExecutorService executorService,
                                                    LocalServerChannel localChannel) {
        this.coapGatewayManager = coapGatewayManager;
        this.serviceUri = serviceUri;
        this.httpRequestProcessor = httpRequestProcessor;
        this.executorService = executorService;
        this.localChannel = localChannel;
        this.nodeRegistrationFuture = SettableFuture.create();
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        try {
            Model model = ResourceToolBox.getModelFromCoapResponse(coapResponse);
            final Map<URI, Model> resources = ResourceToolBox.getModelsPerSubject(model);

            final Date expiry = ResourceToolBox.getExpiryFromCoapResponse(coapResponse);

            final List<Boolean> resourceProxyUris =
                    Collections.synchronizedList(new ArrayList<Boolean>(resources.keySet().size()));

            for(final URI resourceUri : resources.keySet()){

                //Register Resource, i.e. create proxy resource URI
                httpRequestProcessor.addResource(resourceUri, serviceUri);
                final SettableFuture<URI> resourceRegistrationFuture =
                        coapGatewayManager.registerResource(resourceUri, httpRequestProcessor);

                //resourceRegistrationFutures.add(resourceRegistrationFuture);

                //Send actual status of newly registered resource to cache
                resourceRegistrationFuture.addListener(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            resourceRegistrationFuture.get();
                            log.info("Succesfully registered resource {} from service {}.", resourceUri, serviceUri);

                            ResourceStatusMessage resourceStatusMessage =
                                    new ResourceStatusMessage(resourceUri, resources.get(resourceUri), expiry);

                            ChannelFuture initialCachingFuture = Channels.write(localChannel, resourceStatusMessage);
                            initialCachingFuture.addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    resourceProxyUris.add(true);
                                    log.info("Registered resource {}/{}", resourceProxyUris.size(), resources.keySet().size());
                                    if (resourceProxyUris.size() == resources.keySet().size()) {
                                        log.info("Succesfully initially cached states of all resources from {}.",
                                                serviceUri);
                                        nodeRegistrationFuture.set(true);
                                    }
                                }
                            });
                        }
                        catch(Exception e){

                            if(e.getCause().getCause() instanceof ResourceAlreadyRegisteredException){
                                log.warn("Resource {} was already registered.",
                                        ((ResourceAlreadyRegisteredException) e.getCause().getCause()).getResourceProxyUri());
                                resourceProxyUris.add(false);
                                log.info("Registered resource {}/{}", resourceProxyUris.size(), resources.keySet().size());
                                if (resourceProxyUris.size() == resources.keySet().size()) {
                                    log.info("Succesfully initially cached states of all resources from {}.",
                                            serviceUri);
                                    nodeRegistrationFuture.set(true);
                                }
                            }
                            else{
                                log.warn("Exception during resource registration.", e.getCause());
                                nodeRegistrationFuture.setException(e);
                            }
                        }

                    }
                }, executorService);
            }
        }
        catch (ProxyServiceException e) {
            log.error("Error while registering resources from {}", serviceUri, e);
        }
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        log.error("Retransmission timeout for CoAP service {}.", serviceUri);
    }

    public SettableFuture<Boolean> getNodeRegistrationFuture() {
        return nodeRegistrationFuture;
    }
}
