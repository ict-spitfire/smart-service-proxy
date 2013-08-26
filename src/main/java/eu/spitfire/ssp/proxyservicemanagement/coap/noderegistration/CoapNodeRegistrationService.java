package eu.spitfire.ssp.proxyservicemanagement.coap.noderegistration;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebService;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.proxyservicemanagement.coap.CoapServiceManager;
import eu.spitfire.ssp.proxyservicemanagement.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import eu.spitfire.ssp.server.pipeline.messages.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;

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

    private CoapServiceManager coapProxyServiceManager;

    //Application to send the service discovery requests
    private CoapClientApplication coapClientApplication;
    private HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices;

    /**
     * @param coapProxyServiceManager the {@link eu.spitfire.ssp.proxyservicemanagement.coap.CoapServiceManager} to register new resources at the proxy
     * @param coapClientApplication the {@link CoapClientApplication} to send resource discovery requests
     *                              (to .well-known/core) upon reception of registration request
     * @param httpRequestProcessorForCoapServices the {@link HttpRequestProcessor} to handle incoming HTTP requests
     *                                            for CoAP resources.
     */
    public CoapNodeRegistrationService(CoapServiceManager coapProxyServiceManager,
                                       CoapClientApplication coapClientApplication,
                                       HttpRequestProcessorForCoapServices httpRequestProcessorForCoapServices){
        super("/here_i_am", Boolean.TRUE);
        this.coapProxyServiceManager = coapProxyServiceManager;
        this.coapClientApplication = coapClientApplication;
        this.httpRequestProcessorForCoapServices = httpRequestProcessorForCoapServices;
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
     *                                      {@link Code#INTERNAL_SERVER_ERROR_500} if another error occured
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
            if(remoteAddress.getAddress() instanceof Inet6Address)
                targetURIHost = "[" + targetURIHost.substring(1) + "]";

            //create request for /.well-known/core and a processor to process the response
            URI targetURI = new URI("coap://" + targetURIHost + ":" + CoapServiceManager.COAP_SERVER_PORT +
                    "/.well-known/core");
            CoapRequest serviceDiscoveryRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI);

            //Create the processor for the .well-known/core resource and a future to wait for set of services
            WellKnownCoreResponseProcessor wellKnownCoreResponseProcessor = new WellKnownCoreResponseProcessor();
            final SettableFuture<Set<String>> serviceDiscoveryFuture = SettableFuture.create();
            wellKnownCoreResponseProcessor.setServiceDiscoveryFuture(serviceDiscoveryFuture);

            serviceDiscoveryFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        Set<ListenableFuture<URI>> resourceRegistrationFutureSet = new HashSet<>();

                        //register new proxy services
                        for (String remoteServicePath : serviceDiscoveryFuture.get()) {
//                            final SettableFuture<URI> serviceRegistrationFuture = SettableFuture.create();
//                            resourceRegistrationFutureSet.add(serviceRegistrationFuture);
                            URI resourceUri = new URI("coap", null, remoteAddress.getAddress().getHostAddress(), -1,
                                                        remoteServicePath, null, null);

                            SettableFuture<URI> resourceRegistrationFuture =
                                    coapProxyServiceManager.registerResource(resourceUri,
                                                        httpRequestProcessorForCoapServices);

                             resourceRegistrationFutureSet.add(resourceRegistrationFuture);
                        }

                        final ListenableFuture<List<URI>> registrationCompletedFuture =
                                Futures.allAsList(resourceRegistrationFutureSet);

                        //Send 201 response when the registration was successful or 500 response in case of an error
                        registrationCompletedFuture.addListener(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    for (URI proxyUri : registrationCompletedFuture.get())
                                        log.info("New service at {}", proxyUri);

                                    nodeRegistrationFuture.set(new CoapResponse(Code.CREATED_201));
                                }
                                catch (Exception e) {
                                    String message = "Error in local service registration process";
                                    if(e.getCause().getCause() instanceof ResourceAlreadyRegisteredException)
                                        log.warn(message, e.getMessage());
                                    else
                                        log.error(message, e);
                                    nodeRegistrationFuture.set(createCoapResponse(Code.INTERNAL_SERVER_ERROR_500,
                                            message + ":\n" + e.getCause()));
                                }
                            }
                        }, CoapNodeRegistrationService.this.getScheduledExecutorService());

                    }
                    catch (Exception e) {
                        if(e instanceof ExecutionException){
                            if(e.getCause() instanceof ResourceDiscoveringTimeoutException){
                                log.error("Timeout during resource discovery of node {}.",
                                        ((ResourceDiscoveringTimeoutException) e.getCause()).getNodeAddress());
                            }
                            log.error("Cause was {}", e.getCause().toString());
                        }

                        String message = "Error in service discovery process.";
                        log.error(message, e);

                        CoapResponse coapResponse = new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
                        try {
                            coapResponse.setPayload(message.getBytes(Charset.forName("UTF-8")));
                        }
                        catch (MessageDoesNotAllowPayloadException e1) {
                           log.error("This should never happen!", e1);
                        }

                        nodeRegistrationFuture.set(coapResponse);
                    }
                }
            }, this.getScheduledExecutorService());

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
