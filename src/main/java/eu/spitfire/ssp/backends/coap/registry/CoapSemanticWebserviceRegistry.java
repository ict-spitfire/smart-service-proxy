package eu.spitfire.ssp.backends.coap.registry;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.backends.BackendComponentFactory;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.coap.observation.CoapWebserviceObserver;
import eu.spitfire.ssp.backends.DataOriginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.09.13
 * Time: 18:30
 * To change this template use File | Settings | File Templates.
 */
public class CoapSemanticWebserviceRegistry extends DataOriginRegistry<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public CoapSemanticWebserviceRegistry(BackendComponentFactory<URI> backendManager) {
        super(backendManager);
    }

    /**
     * Invokation of this method causes the framework to send a CoAP request to the .well-known/core service
     * at the given remote address on port 5683. All services from the list returned by that service are
     * considered data origins to be registered at the SSP.
     *
     * @param registrationResponseFuture the {@link SettableFuture} to be set with the CoAP response which is then
     *                                   returned as response on the registration request
     * @param remoteAddress the IP address of the host offering CoAP Webservices as data origins
     */
    public void processRegistrationRequest(final SettableFuture<CoapResponse> registrationResponseFuture,
                                                             final InetAddress remoteAddress){
        try{
            log.debug("Process registration request from {}.", remoteAddress.getAddress());

            String targetURIHost = remoteAddress.toString();

            //This is due to the string representation of IP addresse in JAVA (leading "/")
            if(targetURIHost.startsWith("/"))
                targetURIHost = targetURIHost.substring(1);

            //create request for /.well-known/core and a processor to process the response
            URI targetURI = new URI("coap", null, targetURIHost, CoapBackendComponentFactory.COAP_SERVER_PORT,
                    "/.well-known/core", null, null);
            final CoapRequest wellKnownCoreRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI);

            //Create the processor for the .well-known/core service
            final WellKnownCoreResponseProcessor wellKnownCoreResponseProcessor = new WellKnownCoreResponseProcessor();

            //Create a future to wait for set of services extracted from the .well-known/core service
            final SettableFuture<Set<String>> serviceDiscoveryFuture =
                    wellKnownCoreResponseProcessor.getServiceDiscoveryFuture();

            serviceDiscoveryFuture.addListener(new Runnable(){
                //register the resources from all services listed in .well-known/core
                @Override
                public void run() {
                    try {
                        Set<String> services = serviceDiscoveryFuture.get();

                        //create task to process the list of services
                        CoapResourcesRegistrationTask coapResourcesRegistrationTask =
                                new CoapResourcesRegistrationTask(services, new InetSocketAddress(remoteAddress, 5683),
                                        registrationResponseFuture);

                        //register the resources from the
                        CoapSemanticWebserviceRegistry.this.backendComponentFactory
                                                      .getScheduledExecutorService()
                                                      .submit(coapResourcesRegistrationTask);
                    }
                    catch (Exception e) {
                        log.error("This should never happen.", e);
                        registrationResponseFuture.setException(e);
                    }
                }
            }, backendComponentFactory.getScheduledExecutorService());


            //write the CoAP request to the .well-known/core resource
            ((CoapBackendComponentFactory) backendComponentFactory).getCoapClientApplication()
                                                 .writeCoapRequest(wellKnownCoreRequest, wellKnownCoreResponseProcessor);
        }
        catch(Exception e){
            log.error("Exception while processing node registration.", e);
            registrationResponseFuture.setException(e);
        }
    }

    public class CoapResourcesRegistrationTask implements Runnable{

        private final Set<String> coapServices;
        private final InetSocketAddress remoteAddress;
        private SettableFuture<CoapResponse> registrationResponseFuture;

        public CoapResourcesRegistrationTask(Set<String> coapServices, InetSocketAddress remoteAddress,
                                             SettableFuture<CoapResponse> registrationResponseFuture){
            this.coapServices = coapServices;
            this.remoteAddress = remoteAddress;
            this.registrationResponseFuture = registrationResponseFuture;
        }

        @Override
        public void run() {
            try {
                //For compatibility with TUBS stuff
                if (coapServices.contains("/rdf"))
                    registerTubsResources(registrationResponseFuture);
                else{
                    log.error("TODO: No /rdf resource contained!");
                    CoapResponse coapResponse = new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
                    registrationResponseFuture.set(coapResponse);
                }
            }
            catch (Exception e) {
                log.error("Error while trying to request a CoAP service", e);
                CoapResponse coapResponse = new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
                registrationResponseFuture.set(coapResponse);
            }
        }

        private void registerTubsResources(final SettableFuture<CoapResponse> registrationResponseFuture)
                throws Exception{

            final URI rdfServiceUri = new URI("coap", null, remoteAddress.getAddress().getHostAddress(), -1,
                    "/rdf", null, null);

            removeAllResources(rdfServiceUri);

            final ListenableFuture<Map<URI, Boolean>> resourceRegistrationResultFuture =
                    registerResourcesFromDataOrigin(rdfServiceUri, backendComponentFactory.getHttpRequestProcessor());

            resourceRegistrationResultFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try{
                        Map<URI, Boolean> registrationResult = resourceRegistrationResultFuture.get();

                        if (log.isInfoEnabled()) {
                            StringBuilder stringBuilder = new StringBuilder();
                            stringBuilder.append("Finished registration of resources from {}: ");
                            for (URI resourceUri : registrationResult.keySet()){
                                stringBuilder.append(resourceUri.toString() +
                                        " :  new = " + registrationResult.get(resourceUri) + ", ");
                            }
                            log.info(stringBuilder.toString());
                        }

                        //Start observation of minimal resources
                        for(String servicePath : coapServices){
                            if(servicePath.endsWith("_minimal")){
                                URI serviceUri = new URI("coap", null, remoteAddress.getAddress().getHostAddress(),
                                        -1, servicePath, null, null);
                                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
                                coapRequest.setObserveOptionRequest();

                                CoapWebserviceObserver observer =
                                        new CoapWebserviceObserver(backendComponentFactory, serviceUri);
//                                String fakeDataOriginPath = servicePath.substring(0, servicePath.indexOf("/_minimal"));
//                                URI fakeDataOrigin = new URI("coap", null, remoteAddress.getAddress().getHostAddress(),
//                                        -1, fakeDataOriginPath, null, null);

                                ((CoapBackendComponentFactory) backendComponentFactory).getCoapClientApplication()
                                        .writeCoapRequest(coapRequest, observer);

                                log.info("Start observation of service {} ", serviceUri);
                            }
                        }
                        registrationResponseFuture.set(new CoapResponse(Code.CREATED_201));
                    }
                    catch (Exception e) {
                        log.error("This should never happen.", e);
                        registrationResponseFuture.setException(e);
                    }
                }
            }, backendComponentFactory.getScheduledExecutorService());
        }
    }



    //    @Override
//    public ListenableFuture<ExpiringModel> readModelFromDataOrigin(URI dataOrigin) {
//        final SettableFuture<ExpiringModel> expiringModelFuture = SettableFuture.create();
//
//        try {
//            //Write Coap request
//            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, dataOrigin);
//            ListenableCoapResponseProcessor responseProcessor = new ListenableCoapResponseProcessor(dataOrigin);
//            backendComponentFactory.getCoapClientApplication().writeCoapRequest(coapRequest, responseProcessor);
//
//            //wait for the response
//            final ListenableFuture<CoapResponse> coapResponseFuture = responseProcessor.getCoapResponseFuture();
//            coapResponseFuture.addListener(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        CoapResponse coapResponse = coapResponseFuture.get();
//                        Model model = ResourceToolbox.getModelFromCoapResponse(coapResponse);
//                        Date expiry = ResourceToolbox.getExpiryFromCoapResponse(coapResponse);
//
//                        expiringModelFuture.set(new ExpiringModel(model, expiry));
//
//                    } catch (Exception e) {
//                        log.error("This should never happen.", e);
//                        expiringModelFuture.setException(e);
//                    }
//                }
//            }, back);
//        }
//        catch (Exception e) {
//            log.error("This should never happen.", e);
//        }
//        finally{
//            return expiringModelFuture;
//        }
//    }
}
