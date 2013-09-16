package eu.spitfire.ssp.backends.coap.noderegistration;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebService;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.backends.coap.CoapBackendManager;
import eu.spitfire.ssp.backends.coap.observation.CoapResourceObserver;
import eu.spitfire.ssp.backends.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;

import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

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
public class CoapNodeRegistrationService extends NotObservableWebService<Boolean> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapBackendManager coapGatewayManager;

    //Client application to send the service discovery requests
    private CoapClientApplication coapClientApplication;
    private HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices;
    private LocalServerChannel localChannel;

    /**
     * @param coapGatewayManager the {@link eu.spitfire.ssp.backends.coap.CoapBackendManager} to register new resources at the proxy
     * @param coapClientApplication the {@link CoapClientApplication} to send resource discovery requests
     *                              (to .well-known/core) upon reception of registration request
     * @param httpRequestProcessorForCoapServices the {@link HttpRequestProcessor} to handle incoming HTTP requests
     *                                            for CoAP resources.
     */
    public CoapNodeRegistrationService(CoapBackendManager coapGatewayManager,
                                       CoapClientApplication coapClientApplication,
                                       HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices,
                                       LocalServerChannel localChannel){
        super("/here_i_am", Boolean.TRUE);
        this.coapGatewayManager = coapGatewayManager;
        this.coapClientApplication = coapClientApplication;
        this.httpRequestProcessorForCoapServices = httpRequestProcessorForCoapServices;
        this.localChannel = localChannel;
    }

    /**
     * Processes the incoming {@link CoapRequest} to register a new node.
     * @param nodeRegistrationFuture this method sets a {@link CoapResponse} with a proper {@link Code} on this
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
    public void processCoapRequest(final SettableFuture<CoapResponse> nodeRegistrationFuture,
                                   CoapRequest registrationRequest, final InetSocketAddress remoteAddress) {

        log.info("Received CoAP registration message from {}: {}", remoteAddress.getAddress(), registrationRequest);

        //Only POST messages are allowed
        if(registrationRequest.getCode() != Code.POST){
            nodeRegistrationFuture.set(createCoapResponse(Code.METHOD_NOT_ALLOWED_405,
                    registrationRequest.getCode().toString()));
            return;
        }

        //Request was POST, so go ahead
        try {
            log.debug("Process registration request from {}.", remoteAddress.getAddress());

            String targetURIHost = remoteAddress.getAddress().toString();
            if(targetURIHost.startsWith("/"))
                targetURIHost = targetURIHost.substring(1);

            //create request for /.well-known/core and a processor to process the response
            URI targetURI = new URI("coap", null, targetURIHost, CoapBackendManager.COAP_SERVER_PORT,
                    "/.well-known/core", null, null);
            CoapRequest serviceDiscoveryRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI);

            //Create the processor for the .well-known/core resource and a future to wait for set of services
            WellKnownCoreResponseProcessor wellKnownCoreResponseProcessor = new WellKnownCoreResponseProcessor();
            final SettableFuture<Set<String>> serviceDiscoveryFuture =
                    wellKnownCoreResponseProcessor.getServiceDiscoveryFuture();

            serviceDiscoveryFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        //For compatibility with TUBS stuff
                        if(serviceDiscoveryFuture.get().contains("/rdf")){

                            URI serviceUri = new URI("coap", null, remoteAddress.getAddress().getHostAddress(), -1,
                                    "/rdf", null, null);
                            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);

                            CoapResponseProcessorToRegisterResources coapResponseProcessor =
                                    new CoapResponseProcessorToRegisterResources(coapGatewayManager, serviceUri,
                                            httpRequestProcessorForCoapServices, getListeningExecutorService(),
                                            localChannel);

                            //Send initial request to put resources to cache
                            log.info("Send request to service {}", serviceUri);
                            coapClientApplication.writeCoapRequest(coapRequest, coapResponseProcessor);

                            coapResponseProcessor.getNodeRegistrationFuture().addListener(new Runnable(){
                                @Override
                                public void run() {
                                    try{
                                        for(String servicePath : serviceDiscoveryFuture.get()){

                                            if(servicePath.endsWith("_minimal")){
                                                //create observation requests
                                                URI serviceUri = new URI("coap", null, remoteAddress.getAddress().getHostAddress(), -1,
                                                        servicePath, null, null);
                                                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
                                                coapRequest.setObserveOptionRequest();

                                                //send observation request
                                                log.info("Start observation of service {}", serviceUri);
                                                coapClientApplication.writeCoapRequest(coapRequest,
                                                        new CoapResourceObserver(coapRequest, getScheduledExecutorService(),
                                                                localChannel));
                                            }
                                        }
                                    }
                                    catch (Exception e){
                                        log.error("Could not register as observer at node {}",
                                                remoteAddress.getAddress().getHostAddress());
                                    }

                                }
                            }, getScheduledExecutorService());

                        }
                        else{
                            //TODO for default (not SPITFIRE)
                        }
                    } catch (Exception e) {
                        log.error("Error while trying to request a CoAP service", e);
                    }
                }
            }, getScheduledExecutorService());

            nodeRegistrationFuture.set(new CoapResponse(Code.CREATED_201));


            //write the CoAP request to the .well-known/core resource
            coapClientApplication.writeCoapRequest(serviceDiscoveryRequest, wellKnownCoreResponseProcessor);

        }
        catch(Exception e){
            log.error("Exception while processing node registration.", e);
            nodeRegistrationFuture.set(createCoapResponse(Code.INTERNAL_SERVER_ERROR_500, e.getCause().toString()));
        }
    }

    private CoapResponse createCoapResponse(Code code, String message){
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
