package eu.spitfire.ssp.proxyservicemanagement.coap.observation;

import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.proxyservicemanagement.AbstractResourceObserver;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.proxyservicemanagement.coap.CoapProxyServiceManager;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 18.04.13
* Time: 09:43
* To change this template use File | Settings | File Templates.
*/
public class CoapResourceObserver extends AbstractResourceObserver implements CoapResponseProcessor,
        RetransmissionTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    private URI resourceUri;
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture scheduledFuture;

    public CoapResourceObserver(URI resourceUri, ScheduledExecutorService scheduledExecutorService,
                                LocalServerChannel localChannel){
        super(localChannel);

        this.resourceUri = resourceUri;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.info("Received response from service {}.", resourceUri);

        if(scheduledFuture != null){
            if(scheduledFuture.cancel(false))
                log.info("Deletion of observed resource {} canceled!", resourceUri);
            else
                log.error("Deletion of observed resource {} could not be canceled!", resourceUri);
        }

        if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){

            log.warn("Observation of {} failed. CoAP response was: {}", resourceUri, coapResponse);
            return;
        }

        //create resource status message to update the cache
        ResourceStatusMessage resourceStatusMessage;
        try {
            resourceStatusMessage = ResourceStatusMessage.create(coapResponse, resourceUri);
            updateResourceStatus(resourceStatusMessage);
        } catch (Exception e) {
            log.warn("Exception while creating resource status message from CoAP update notification.");
            return;
        }

        //schedule removal of the resource after expiry
        long maxAge = coapResponse.getMaxAge();
        scheduledFuture = scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                log.info("Start removal of service because of expiry: {}.", resourceUri);
                removeResource(new InternalRemoveResourceMessage(resourceUri));
            }
        }, maxAge, TimeUnit.SECONDS);
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        scheduledExecutorService.schedule(new Runnable(){
            @Override
            public void run() {
                removeResource(new InternalRemoveResourceMessage(resourceUri));
            }
        }, 0, TimeUnit.SECONDS) ;
    }

//    private Channel createChannelForInternalMessages() throws Exception {
//        ChannelFactory channelFactory = new DefaultLocalClientChannelFactory();
//
//        ChannelPipelineFactory pipelineFactory = new ResourceUpdateChannelPipelineFactory(coapProxyServiceManager);
//
//        return channelFactory.newChannel(pipelineFactory.getPipeline());
//    }
//
//    @Override
//    public void receiveEmptyACK() {
//        log.info("Received empty ACK for request from " + serviceToObservePath + " at " + serviceToObserveHost);
//    }
//
//    @Override
//    public void handleRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
//        log.info("Giving up retransmitting request to " + serviceToObservePath + " at " + serviceToObserveHost);
//        try {
//            Channel internalChannel = createChannelForInternalMessages();
//            ChannelFuture future =
//                    internalChannel.write(new ObservingFailedMessage(serviceToObserveHost, serviceToObservePath));
//            future.addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture channelFuture) throws Exception {
//                    if(!channelFuture.isSuccess()){
//                        log.debug("Finished with error: " + channelFuture.getCause());
//                    }
//                }
//            });
//            future.addListener(ChannelFutureListener.CLOSE);
//
//        } catch (Exception e) {
//            log.error("Unexpected error: ", e);
//        }
//    }


}
