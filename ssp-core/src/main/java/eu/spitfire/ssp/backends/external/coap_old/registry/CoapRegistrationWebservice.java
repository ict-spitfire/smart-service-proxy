//package eu.spitfire.ssp.backends.external.coap.registry;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.SettableFuture;
//import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebservice;
//import de.uniluebeck.itm.ncoap.message.CoapRequest;
//import de.uniluebeck.itm.ncoap.message.CoapResponse;
//import de.uniluebeck.itm.ncoap.message.MessageCode;
//import de.uniluebeck.itm.ncoap.message.options.OptionValue;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;
//
///**
// * This is the WebService for new sensor nodes to register. It's path is <code>/here_i_am</code>. It only accepts
// * {@link CoapRequest}s with code {@link de.uniluebeck.itm.ncoap.message.MessageCode.Name#POST}. Any contained payload
// * is ignored.
// *
// * Upon reception of such a request the service sends a {@link CoapRequest} with
// * {@link de.uniluebeck.itm.ncoap.message.MessageCode.Name#GET} to the <code>/.well-known/core</code> resource of the
// * sensor node to discover the services available on the new node.
// *
// * Upon discovery of the available services it responds to the original registration request with a proper response
// * code.
// *
// * @author Oliver Kleine
// */
//public class CoapRegistrationWebservice extends NotObservableWebservice<Boolean> {
//
//    private static Logger log = LoggerFactory.getLogger(CoapRegistrationWebservice.class.getName());
//
//    private CoapRegistry coapWebserviceRegistry;
//    private ExecutorService internalTasksExecutorService;
//
//    public CoapRegistrationWebservice(CoapRegistry coapWebserviceRegistry, ExecutorService internalTasksExecutorService){
//        super("/here_i_am", Boolean.TRUE, OptionValue.MAX_AGE_DEFAULT);
//        this.coapWebserviceRegistry = coapWebserviceRegistry;
//        this.internalTasksExecutorService = internalTasksExecutorService;
//    }
//
//    @Override
//    public void processCoapRequest(final SettableFuture<CoapResponse> registrationResponseFuture,
//                                   final CoapRequest coapRequest, InetSocketAddress remoteAddress) {
//
//        log.info("Received CoAP registration message from {}: {}", remoteAddress.getAddress(), coapRequest);
//
//        //Only POST messages are allowed
//        if(coapRequest.getMessageCodeName() != MessageCode.Name.POST){
//            CoapResponse coapResponse = CoapResponse.createErrorResponse(coapRequest.getMessageTypeName(),
//                    MessageCode.Name.METHOD_NOT_ALLOWED_405, "Only POST messages are allowed!");
//
//            registrationResponseFuture.set(coapResponse);
//            return;
//        }
//
//        //Request was POST, so go ahead
//        final ListenableFuture<Set<URI>> registeredResourcesFuture =
//                coapWebserviceRegistry.processRegistration(remoteAddress.getAddress());
//
//        registeredResourcesFuture.addListener(new Runnable(){
//            @Override
//            public void run() {
//                try{
//                    Set<URI> registeredResources = registeredResourcesFuture.get();
//
//                    if(log.isInfoEnabled()){
//                        for(URI resourceUri : registeredResources)
//                            log.info("Successfully registered resource {}", resourceUri);
//                    }
//
//                    CoapResponse coapResponse = new CoapResponse(coapRequest.getMessageTypeName(),
//                            MessageCode.Name.CREATED_201);
//                    registrationResponseFuture.set(coapResponse);
//                }
//                catch(Exception ex){
//                    CoapResponse coapResponse = CoapResponse.createErrorResponse(coapRequest.getMessageTypeName(),
//                            MessageCode.Name.INTERNAL_SERVER_ERROR_500, ex.getMessage());
//                    registrationResponseFuture.set(coapResponse);
//                }
//            }
//        }, internalTasksExecutorService);
//    }
//
//
//    @Override
//    public byte[] getSerializedResourceStatus(long l) {
//        return new byte[0];
//    }
//
//
//    @Override
//    public byte[] getEtag(long l) {
//        return new byte[0];
//    }
//
//
//    @Override
//    public void updateEtag(Boolean aBoolean) {
//        //Nothing to do as only POST is allowed!
//    }
//
//
//    @Override
//    public void shutdown() {
//        //Nothing to do
//    }
//}
