package eu.spitfire.ssp.backends.coap.noderegistration;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebService;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import eu.spitfire.ssp.backends.coap.CoapBackendManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

/**
 * This is the WebService for new sensor nodes to register. It's path is <code>/here_i_am</code>. It only accepts
 * {@link CoapRequest}s with code {@link Code#POST}. Any contained payload is ignored.
 *
 * Upon reception of such a request the service sends a {@link CoapRequest} with {@link Code#GET} to the
 * <code>/.well-known/core</code> resource of the sensor node to discover the services available on the new node.
 *
 * Upon discovery of the available services it responds to the original registration request with a proper response
 * code.
 *
 * @author Oliver Kleine
*/
public class CoapRegistrationWebservice extends NotObservableWebService<Boolean>{

    private static Logger log = LoggerFactory.getLogger(CoapRegistrationWebservice.class.getName());

    private CoapSemanticWebserviceRegistry coapSemanticWebserviceRegistry;
    //private CoapBackendManager backendManager;

    public CoapRegistrationWebservice(CoapBackendManager backendManager){
        super("/here_i_am", Boolean.TRUE);
        //this.backendManager = backendManager;
        this.coapSemanticWebserviceRegistry = (CoapSemanticWebserviceRegistry) backendManager.getDataOriginRegistry();
    }

    /**
     * Processes the incoming {@link CoapRequest} to register a new node.
     * @param registrationResponseFuture this method sets a {@link CoapResponse} with a proper {@link Code} on this
     *                               {@link SettableFuture} to indicate success or failure of the registration
     *                               process asynchronously:
     *                               <ul>
     *                                  <li>
     *                                      {@link Code#CREATED_201} if the discovery process to discover the nodes
     *                                      services was successfully finished.
     *                                  </li>
     *                                  <li>
     *                                      {@link Code#METHOD_NOT_ALLOWED_405} if the request code was not
     *                                      {@link Code#POST}
     *                                  </li>
     *                                  <li>
     *                                      {@link Code#INTERNAL_SERVER_ERROR_500} if another error occurred
     *                                  </li>
     *                              </ul>
     * @param registrationRequest the {@link CoapRequest} to be processed
     * @param remoteAddress the address of the sender of the request
     */
    @Override
    public void processCoapRequest(final SettableFuture<CoapResponse> registrationResponseFuture,
                                   CoapRequest registrationRequest, final InetSocketAddress remoteAddress) {

        log.info("Received CoAP registration message from {}: {}", remoteAddress.getAddress(), registrationRequest);

        //Only POST messages are allowed
        if(registrationRequest.getCode() != Code.POST){
            registrationResponseFuture.set(createCoapResponse(Code.METHOD_NOT_ALLOWED_405,
                    registrationRequest.getCode().toString()));
            return;
        }

        //Request was POST, so go ahead
        this.coapSemanticWebserviceRegistry
            .processRegistrationRequest(registrationResponseFuture, remoteAddress.getAddress());

//        //Wait for internal registration to be processed
//        internalRegistrationFuture.addListener(new Runnable() {
//            @Override
//            public void run() {
//                CoapResponse coapResponse;
//                try {
//                    coapResponse = new CoapResponse(internalRegistrationFuture.get());
//                }
//                catch (Exception e) {
//                    coapResponse = createCoapResponse(Code.INTERNAL_SERVER_ERROR_500, e.getCause().toString());
//                }
//
//                registrationResponseFuture.set(coapResponse);
//            }
//        }, this.getListeningExecutorService());
    }


    public static CoapResponse createCoapResponse(Code code, String message){
        CoapResponse coapResponse = new CoapResponse(code);

        try {
            coapResponse.setPayload(message.getBytes(Charset.forName("UTF-8")));
        }
        catch (MessageDoesNotAllowPayloadException e) {
            log.error("This should never happen!", e);
        }

        return coapResponse;
    }

    @Override
    public void shutdown() {
        //Nothing to do
    }
}
