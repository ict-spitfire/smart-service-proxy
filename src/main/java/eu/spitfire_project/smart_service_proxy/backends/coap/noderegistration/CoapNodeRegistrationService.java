package eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.webservice.NotObservableWebService;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 12.04.13
 * Time: 15:30
 * To change this template use File | Settings | File Templates.
 */
class CoapNodeRegistrationService extends NotObservableWebService<Boolean> {

    private static Logger log = Logger.getLogger(CoapNodeRegistrationService.class.getName());

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    CoapNodeRegistrationService(){
        super("/here_i_am", Boolean.TRUE);
    }

    @Override
    public CoapResponse processMessage(CoapRequest request, InetSocketAddress remoteAddress) {

        //Only POST messages are allowed
        if(request.getCode() != Code.POST){
            return new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
        }
        else{
            try {
                log.debug("Registration request from " + remoteAddress.getAddress());
                CoapResourceDiscoverer resourceDiscoverer = new CoapResourceDiscoverer(remoteAddress.getAddress());
                ScheduledFuture<Boolean> future =
                    executorService.schedule(resourceDiscoverer, 0, TimeUnit.MILLISECONDS);
                if(future.get(2, TimeUnit.MINUTES)){
                    return new CoapResponse(Code.CREATED_201);
                }
                return new CoapResponse(Code.GATEWAY_TIMEOUT_504);
            }
            catch (InterruptedException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
            }
            catch (ExecutionException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
            }
            catch (TimeoutException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
            }
        }
    }

    /**
     * This method is invoked by the nCoAP framework whenever a new incoming CoAP request is to be processed. It only
     * accepts requests with {@link de.uniluebeck.itm.spitfire.nCoap.message.header.Code#GET} for the resource /here_i_am. All other requests will cause failure
     * responses ({@link de.uniluebeck.itm.spitfire.nCoap.message.header.Code#NOT_FOUND_404} for other resources or {@link de.uniluebeck.itm.spitfire.nCoap.message.header.Code#METHOD_NOT_ALLOWED_405} for
     * other methods).
     *                            public void fakeRegistration(InetAddress inetAddress){
//        executorService.schedule(new CoapResourceDiscoverer(inetAddress), 0, TimeUnit.SECONDS);
//    }
     * @param coapRequest
     * @param remoteSocketAddress
     * @return
     */
//    @Override
//    public CoapResponse receiveCoapRequest(CoapRequest coapRequest, InetSocketAddress remoteSocketAddress) {
//
//        log.debug("Received request from " +
//                remoteSocketAddress.getAddress().getHostAddress() + ":" + remoteSocketAddress.getPort()
//                + " for resource " + coapRequest.getTargetUri());
//
//        CoapResponse coapResponse = null;
//
//        if(coapRequest.getTargetUri().getPath().equals("/here_i_am")){
//            if(coapRequest.getCode() == Code.POST){
//                if(coapRequest.getMessageType() == MsgType.CON){
//                    coapResponse =  new CoapResponse(MsgType.ACK, Code.CONTENT_205);
//                }
//
//                //Node registration
//                log.debug("Schedule sending of request for .well-known/core");
//                executorService.schedule(new NodeRegistration(remoteSocketAddress.getAddress()),
//                        0, TimeUnit.SECONDS);
//
//                //Automatic annotation required
//                if(coapRequest.getPayload().readableBytes() > 0){
//                    log.debug("Request payload: " + coapRequest.getPayload().toString(Charset.forName("UTF-8")));
//                }
//            }
//            else{
//                coapResponse = new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
//            }
//        }
//        else{
//            coapResponse = new CoapResponse(Code.NOT_FOUND_404);
//        }
//        return coapResponse;
//    }
}
