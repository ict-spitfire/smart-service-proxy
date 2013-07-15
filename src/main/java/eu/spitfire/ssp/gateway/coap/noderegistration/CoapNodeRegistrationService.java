package eu.spitfire.ssp.gateway.coap.noderegistration;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebService;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.gateway.coap.CoapProxyServiceCreator;
import eu.spitfire.ssp.gateway.coap.requestprocessing.HttpRequestProcessorForCoapServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

/**
* This is the WebService for new sensor nodes to register at. It's path is <code>/here_i_am</code>. It only accepts
* {@link CoapRequest}s with code {@link Code#POST}. Any contained payload is ignored.
*
* Upon reception of such a request the service sends a {@link CoapRequest} with {@link Code#GET} to the
* <code>/.well-known/core</code> resource of the sensor node to discover the services available on the new node.
*
* @author Oliver Kleine
*/
public class CoapNodeRegistrationService extends NotObservableWebService<Boolean> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapProxyServiceCreator coapProxyServiceCreator;
    private HttpRequestProcessorForCoapServices requestProcessor;

    public CoapNodeRegistrationService(CoapProxyServiceCreator coapProxyServiceCreator){
        super("/here_i_am", Boolean.TRUE);
        this.coapProxyServiceCreator = coapProxyServiceCreator;
        this.requestProcessor = new HttpRequestProcessorForCoapServices(coapProxyServiceCreator.getCoapClient());
    }

    /**
     * Returns an empty {@link CoapResponse} with the proper {@link Code}
     *
     * @param incomingRequest The {@link CoapRequest} to be processed
     * @param remoteAddress The address of the sender of the request
     * @return an empty {@link CoapResponse} instance (i.e. without payload) with code
     *          <ul>
     *              <li>
     *                  {@link Code#CREATED_201} if the discovery process to discover the nodes services was
     *                  successfully finished.
     *              </li>
     *              <li>
     *                  {@link Code#METHOD_NOT_ALLOWED_405} if the request code was not {@link Code#POST}
     *              </li>
     *              <li>
     *                  {@link Code#INTERNAL_SERVER_ERROR_500} if another error occured
     *              </li>
     *          </ul>
     */
    @Override
    public void processCoapRequest(final SettableFuture<CoapResponse> nodeRegistrationFuture, CoapRequest incomingRequest,
                                   final InetSocketAddress remoteAddress) {
        log.info("Process registration message from " + remoteAddress.getAddress());

        //Only POST messages are allowed
        if(incomingRequest.getCode() != Code.POST)
            nodeRegistrationFuture.set(new CoapResponse(Code.METHOD_NOT_ALLOWED_405));


        try {
            log.debug("Process registration request from {}.", remoteAddress.getAddress());

            String targetURIHost = remoteAddress.getAddress().toString();
            if(remoteAddress.getAddress() instanceof Inet6Address)
                targetURIHost = "[" + targetURIHost.substring(1) + "]";

            //create request for /.well-known/core and a processor to process the response
            URI targetURI = new URI("coap://" + targetURIHost + ":" + CoapProxyServiceCreator.NODES_COAP_PORT +
                    "/.well-known/core");
            CoapRequest discoveringRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI);
            WellKnownCoreProcessor wellKnownCoreProcessor = new WellKnownCoreProcessor();

            //future to indicate whether list of services was succesfully retrieved from sensor node
            final ListenableFuture<Set<String>> remoteServiceDiscoveringFuture =
                    wellKnownCoreProcessor.getDiscoveringFuture();

            remoteServiceDiscoveringFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        Set<ListenableFuture<URI>> localServiceRegistrationFutures = new HashSet<>();

                        //register new proxy services
                        for (String servicePath : remoteServiceDiscoveringFuture.get()) {
                            final SettableFuture<URI> localServiceRegistrationFuture = SettableFuture.create();
                            localServiceRegistrationFutures.add(localServiceRegistrationFuture);

                            coapProxyServiceCreator.registerService(localServiceRegistrationFuture, remoteAddress.getAddress(),
                                    servicePath, requestProcessor);
                        }

                        final ListenableFuture<List<URI>> registrationDoneFuture =
                                Futures.allAsList(localServiceRegistrationFutures);

                        //Send 201 response when the registration was successful or 500 response in case of an error
                        registrationDoneFuture.addListener(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    for(URI proxyUri : registrationDoneFuture.get())
                                        log.info("New service at {}", proxyUri);

                                    nodeRegistrationFuture.set(new CoapResponse(Code.CREATED_201));
                                }
                                catch (Exception e) {
                                    log.error("Error during local servie registration process.", e);
                                    nodeRegistrationFuture.set(new CoapResponse(Code.INTERNAL_SERVER_ERROR_500));
                                }
                            }
                        }, getScheduledExecutorService());

                    } catch (Exception e) {
                        log.error("This should never happen.", e);
                        nodeRegistrationFuture.set(new CoapResponse(Code.INTERNAL_SERVER_ERROR_500));
                    }
                }
            }, this.getScheduledExecutorService());

            //write the CoAP request to the .well-known/core resource
            coapProxyServiceCreator.getCoapClient().writeCoapRequest(discoveringRequest, wellKnownCoreProcessor);

        }
        catch (Exception e) {
            log.error("This should never happen.", e);
            nodeRegistrationFuture.set(new CoapResponse(Code.INTERNAL_SERVER_ERROR_500));
        }
    }

    @Override
    public void shutdown() {
        //Nothing to do
    }
}
