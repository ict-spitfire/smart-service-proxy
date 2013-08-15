package eu.spitfire.ssp.gateway.coap.observation;

import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import eu.spitfire.ssp.core.pipeline.messages.ResourceStatusMessage;
import eu.spitfire.ssp.gateway.coap.CoapProxyServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;


/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 18.04.13
* Time: 09:43
* To change this template use File | Settings | File Templates.
*/
public class CoapResourceObserver implements CoapResponseProcessor, RetransmissionTimeoutProcessor{

    private Logger log =  LoggerFactory.getLogger(this.getClass().getName());

    private CoapProxyServiceManager coapProxyServiceManager;
    private URI observableServiceUri;

    public CoapResourceObserver(CoapProxyServiceManager coapProxyServiceManager, URI observableServiceUri){

        this.coapProxyServiceManager = coapProxyServiceManager;
        this.observableServiceUri = observableServiceUri;
    }

    @Override
    public void processCoapResponse(CoapResponse coapResponse) {
        log.info("Received response from service {}.", observableServiceUri);

        if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){

            log.warn("Observation of {} failed. CoAP response was: {}", observableServiceUri, coapResponse);
            return;
        }

        //Create ResourceStatusMessage
        ResourceStatusMessage resourceStatusMessage;
        try {
            resourceStatusMessage = ResourceStatusMessage.create(coapResponse, observableServiceUri);
//            coapProxyServiceManager.updateCachedResourceStatus(resourceStatusMessage);
        } catch (Exception e) {
            log.warn("Exception while creating resource status message from CoAP update notification.");
            return;
        }
    }

    @Override
    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
        //timeoutMessage.getRemoteAddress()
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
