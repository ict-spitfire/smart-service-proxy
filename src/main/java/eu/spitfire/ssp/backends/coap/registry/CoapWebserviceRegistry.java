package eu.spitfire.ssp.backends.coap.registry;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.InvalidMessageException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import de.uniluebeck.itm.ncoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.ncoap.message.options.ToManyOptionsException;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.coap.observation.CoapWebserviceObserver;
import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.09.13
 * Time: 18:30
 * To change this template use File | Settings | File Templates.
 */
public class CoapWebserviceRegistry extends DataOriginRegistry<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapClientApplication coapClientApplication;
    private CoapServerApplication coapServerApplication;
    private ExecutorService executorService;

    public CoapWebserviceRegistry(CoapBackendComponentFactory backendComponentFactory) {
        super(backendComponentFactory);
        this.coapClientApplication = backendComponentFactory.getCoapClientApplication();
        this.executorService = backendComponentFactory.getScheduledExecutorService();
        this.coapServerApplication = backendComponentFactory.getCoapServerApplication();

        CoapRegistrationWebservice registrationWebservice = new CoapRegistrationWebservice(this, executorService);
        coapServerApplication.registerService(registrationWebservice);
    }


    /**
     * Invokation of this method causes the framework to send a CoAP request to the .well-known/core service
     * at the given remote address on port 5683. All services from the list returned by that service are
     * considered data origins to be registered at the SSP.
     *
     * @param remoteAddress the IP address of the host offering CoAP Webservices as data origins
     */
    public ListenableFuture<Set<URI>> processRegistration(final InetAddress remoteAddress){

        log.info("Process registration request from {}.", remoteAddress.getAddress());
        final SettableFuture<Set<URI>> registeredResourcesFuture = SettableFuture.create();

        //This is due to the string representation of IP addresse in JAVA (leading "/")
        final String host = remoteAddress.getHostAddress();
//        if(remoteAddress.toString().startsWith("/"))
//            webserviceHost = remoteAddress.toString().substring(1);
//        else
//            webserviceHost = remoteAddress.toString();

        //discover all available webservices using the well-known/core webservice
        final ListenableFuture<Set<String>> wellKnownCoreFuture = discoverAvailableService(host);

        //register the resources from all services listed in .well-known/core
        wellKnownCoreFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    Set<String> webservices = wellKnownCoreFuture.get();

                    if(webservices.contains("/rdf"))
                        registerTubsResources(registeredResourcesFuture, webservices, host);
                    else
                        registeredResourcesFuture.setException(new Exception("No /rdf webservice!"));
                }
                catch (Exception e) {
                    log.error("This should never happen.", e);
                    registeredResourcesFuture.setException(e);
                }
            }
        }, executorService);

        return registeredResourcesFuture;

    }

    private ListenableFuture<Set<String>> discoverAvailableService(String host){
        SettableFuture<Set<String>> wellKnownCoreFuture = SettableFuture.create();

        try{
            //create request for /.well-known/core and a processor to process the response
            URI wellKnownCoreUri = new URI("coap", null, host, CoapBackendComponentFactory.COAP_SERVER_PORT,
                    "/.well-known/core", null, null);

            log.info("Discover available services at {} ", wellKnownCoreUri);
            CoapRequest wellKnownCoreRequest = new CoapRequest(MsgType.CON, Code.GET, wellKnownCoreUri);

            //Create the processor for the .well-known/core service
            WellKnownCoreResponseProcessor wellKnownCoreResponseProcessor =
                    new WellKnownCoreResponseProcessor(wellKnownCoreFuture);

            //write the CoAP request to the .well-known/core resource
            coapClientApplication.writeCoapRequest(wellKnownCoreRequest, wellKnownCoreResponseProcessor);
        }
        catch(Exception e){
            wellKnownCoreFuture.setException(e);
        }

        return wellKnownCoreFuture;
    }

    private void registerTubsResources(SettableFuture<Set<URI>> registeredResourcesFuture, Set<String> webservices,
                                       String host){
        try{
            final URI rdfServiceUri = new URI("coap", null, host, -1, "/rdf", null, null);


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
    //                                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
    //                                coapRequest.setObserveOptionRequest();

                                CoapWebserviceObserver observer =
                                        backendComponentFactory.createDataOriginObservers(rdfServiceUri, serviceUri);

                                //backendResourceManager.addObserver(rdfServiceUri, observer);

                                log.info("Start observation of service {} ", serviceUri);
                                if(observer != null)
                                    observer.startObservation();
                                Thread.sleep(2000);
                            }
                        }
                        registrationFuture.set(true);
                    }
                    catch (Exception e) {
                        log.error("This should never happen.", e);
                        registrationFuture.set(false);
                    }
                }
            }, scheduledExecutorService);
        }
        catch(URISyntaxException e){

        }
    }

    private ListenableFuture<Map<URI, Model>> getResourcesFromWebservice(URI webservice){
        SettableFuture<Map<URI, Model>> resourcesFromWebserviceFuture = SettableFuture.create();

        try {
            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, webservice);
            coapRequest.setAccept()
            coapClientApplication.writeCoapRequest(coapRequest, );

        }
        catch (Exception e) {
            log.error("Exception while trying to get resources from {}", webservice, e);
            resourcesFromWebserviceFuture.setException(e);
        }

        return resourcesFromWebserviceFuture;
    }
    public class ResourcesRegistrationTask implements Runnable{

        private final Set<String> coapServices;
        private final InetSocketAddress remoteAddress;

        public ResourcesRegistrationTask(Set<String> coapServices, InetSocketAddress remoteAddress){
            this.coapServices = coapServices;
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void run() {
            try {
                //For compatibility with TUBS stuff
                if (coapServices.contains("/rdf"))
                    registerTubsResources(registrationFuture);
                else{
                    log.error("TODO: No /rdf resource contained!");
                    registrationFuture.set(false);
                }
            }
            catch (Exception e) {
                log.error("Error while trying to request a CoAP service", e);
                registrationFuture.set(false);
            }
        }


    }
}
