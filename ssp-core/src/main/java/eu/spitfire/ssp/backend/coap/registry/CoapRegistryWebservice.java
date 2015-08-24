package eu.spitfire.ssp.backend.coap.registry;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebservice;
import de.uniluebeck.itm.ncoap.application.server.webservice.linkformat.LinkAttribute;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;
import de.uniluebeck.itm.ncoap.message.options.OptionValue;
import eu.spitfire.ssp.backend.coap.CoapComponentFactory;
import eu.spitfire.ssp.backend.coap.CoapWebservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link CoapRegistryWebservice} processes incoming
 * POST requests to the "/registry" URI. Upon reception of a POST requests it discovers the available Web Services at
 * the sender of the POST request by sending a GET request to the senders ".well-known/core" resource and
 * starts to observe all the discovered Web Services.
 *
 * @author Oliver Kleine
 */
public class CoapRegistryWebservice extends NotObservableWebservice<Void> {

    private Logger log = LoggerFactory.getLogger(CoapRegistryWebservice.class.getName());

    private CoapClientApplication coapClient;
    private ScheduledExecutorService internalTasksExecutor;
    private CoapRegistry registry;

    /**
     * Creates a new instance of {@link CoapRegistryWebservice}.
     *
     * @param componentFactory the {@link eu.spitfire.ssp.backend.coap.CoapComponentFactory} to
     *                         get the {@link de.uniluebeck.itm.ncoap.application.client.CoapClientApplication}, and the
     *                         {@link CoapRegistry} from.
     */
    public CoapRegistryWebservice(CoapComponentFactory componentFactory){
        super("/registry", null, OptionValue.MAX_AGE_DEFAULT);
        this.coapClient = componentFactory.getCoapClient();
        this.internalTasksExecutor = componentFactory.getInternalTasksExecutor();
        this.registry = componentFactory.getRegistry();
    }


    @Override
    public void processCoapRequest(final SettableFuture<CoapResponse> registrationResponseFuture,
                                   final CoapRequest coapRequest, InetSocketAddress remoteAddress) {

        try{
            log.info("Received CoAP registration message from {}: {}", remoteAddress.getAddress(), coapRequest);

            //Only POST message are allowed
            if(coapRequest.getMessageCodeName() != MessageCode.Name.POST){
                CoapResponse coapResponse = CoapResponse.createErrorResponse(coapRequest.getMessageTypeName(),
                        MessageCode.Name.METHOD_NOT_ALLOWED_405, "Only POST messages are allowed!");

                registrationResponseFuture.set(coapResponse);
                return;
            }

            //Get the set of available Services on the newly registered Server
            ListenableFuture<Set<URI>> servicesFuture = getAvailableWebservices(remoteAddress.getAddress());

            Futures.addCallback(servicesFuture, new FutureCallback<Set<URI>>() {
                @Override
                public void onSuccess(Set<URI> result) {

                }

                @Override
                public void onFailure(Throwable t) {
                    registrationResponseFuture.setException(t);
                }
            });
        }
        catch(Exception ex){
            registrationResponseFuture.setException(ex);
        }
    }

    /**
     * Returns an empty byte array as there is only POST allowed and no content provided. However, this method is only
     * implemented for the sake of completeness and is not used at all by the framework.
     *
     * @param contentFormat the number representing the desired content format
     *
     * @return an empty byte array
     */
    @Override
    public byte[] getSerializedResourceStatus(long contentFormat) {
        return new byte[0];
    }


    private ListenableFuture<Set<URI>> getAvailableWebservices(final InetAddress remoteAddress) throws Exception {

        final SettableFuture<Set<URI>> servicesFuture = SettableFuture.create();
        final String remoteHostName = remoteAddress.getHostName();

        URI uri = new URI("coap", null, remoteHostName, 5683, "/.well-known/core", null, null);
        CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, uri);
        WellKnownCoreProcessor responseProcessor = new WellKnownCoreProcessor(internalTasksExecutor);

        this.coapClient.sendCoapRequest(coapRequest, responseProcessor, new InetSocketAddress(remoteAddress, 5683));

        Futures.addCallback(responseProcessor.getWellKnownCoreFuture(),
                new FutureCallback<Multimap<String, LinkAttribute>>() {

                    @Override
                    public void onSuccess(Multimap<String, LinkAttribute> result) {
                        try{
                            if(result == null){
                                servicesFuture.set(new HashSet<>());
                                return;
                            }

                            Set<URI> serviceUris = new HashSet<>(result.keySet().size());
                            for(String servicePath : result.keySet()){
//                        String path = "/" + servicePath.substring(1, servicePath.length() - 1);
                                URI serviceUri = new URI("coap", null, remoteHostName, 5683, "/" + servicePath, null, null);
                                serviceUris.add(serviceUri);

                                CoapWebservice coapWebservice = new CoapWebservice(serviceUri);
                                registry.registerDataOrigin(coapWebservice);
                            }

                            servicesFuture.set(serviceUris);
                        }
                        catch(Exception ex){
                            log.error("This should never happen!", ex);
                            servicesFuture.setException(ex);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("This should never happen!", t);
                        servicesFuture.setException(t);
                    }
                });

        return servicesFuture;
    }

    @Override
    public byte[] getEtag(long contentFormat) {
        return new byte[0];
    }

    @Override
    public void updateEtag(Void resourceStatus) {

    }

    @Override
    public void shutdown() {

    }
}
