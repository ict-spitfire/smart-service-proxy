package eu.spitfire.ssp.backends.coap.registry;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType;
import edu.emory.mathcs.backport.java.util.Collections;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.coap.CoapWebserviceResponseProcessor;
import eu.spitfire.ssp.backends.coap.observation.CoapWebserviceObserver;
import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import eu.spitfire.ssp.backends.generic.ExpiringModel;
import eu.spitfire.ssp.backends.generic.ResourceToolbox;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
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

    private CoapBackendComponentFactory backendComponentFactory;
    private CoapClientApplication coapClientApplication;
    private CoapServerApplication coapServerApplication;
    private ExecutorService executorService;

    public CoapWebserviceRegistry(CoapBackendComponentFactory backendComponentFactory) {
        super(backendComponentFactory);
        this.backendComponentFactory = backendComponentFactory;
        this.coapClientApplication = backendComponentFactory.getCoapClientApplication();
        this.executorService = backendComponentFactory.getScheduledExecutorService();
        this.coapServerApplication = backendComponentFactory.getCoapServerApplication();

        CoapRegistrationWebservice registrationWebservice = new CoapRegistrationWebservice(this, executorService);
        coapServerApplication.registerService(registrationWebservice);
        log.info("Registered CoAP registration Webservice ({})", registrationWebservice.getPath());
    }


    /**
     * Invokation of this method causes the framework to send a CoAP request to the .well-known/core service
     * at the given remote address on port 5683. All services from the list returned by that service are
     * considered data origins to be registered at the SSP.
     *
     * @param remoteAddress the IP address of the host offering CoAP Webservices as data origins
     */
    public ListenableFuture<Set<URI>> processRegistration(final InetAddress remoteAddress){

        log.info("Process registration request from {}.", remoteAddress.getHostAddress());
        final SettableFuture<Set<URI>> registeredResourcesFuture = SettableFuture.create();
        final String host = remoteAddress.getHostAddress();

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
                    else{
                        //registeredResourcesFuture.setException(new Exception("No /rdf webservice!"));
                        registerResources(registeredResourcesFuture, webservices, host);
                    }

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
                    new WellKnownCoreResponseProcessor(wellKnownCoreFuture, executorService);

            //write the CoAP request to the .well-known/core resource
            coapClientApplication.writeCoapRequest(wellKnownCoreRequest, wellKnownCoreResponseProcessor);
        }
        catch(Exception e){
            wellKnownCoreFuture.setException(e);
        }

        return wellKnownCoreFuture;
    }

    private void registerResources(final SettableFuture<Set<URI>> registeredResourcesFuture,
                                    final Set<String> webservices, final String host){

        final Map<URI, Boolean> registeredResources = Collections.synchronizedMap(new HashMap<URI, Boolean>());

        for(String path : webservices){
            try{
                final URI webserviceUri = new URI("coap", null, host, -1, path, null, null);

                final SettableFuture<InternalResourceStatusMessage> resourceStatusFuture = SettableFuture.create();

                CoapWebserviceResponseProcessor coapResponseProcessor =
                        new CoapWebserviceResponseProcessor(backendComponentFactory, resourceStatusFuture,
                                webserviceUri, webserviceUri);

                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, webserviceUri);
                coapRequest.setAccept(MediaType.APP_SHDT, MediaType.APP_RDF_XML, MediaType.APP_N3, MediaType.APP_TURTLE);

                coapClientApplication.writeCoapRequest(coapRequest, coapResponseProcessor);

                resourceStatusFuture.addListener(new Runnable(){
                    @Override
                    public void run() {
                        try{
                            resourceStatusFuture.get();
                            CoapWebserviceObserver observer =
                                    new CoapWebserviceObserver(backendComponentFactory, webserviceUri);
                            observer.startObservation();

                            registeredResources.put(webserviceUri, true);

                        }
                        catch(Exception e){
                            log.error("Failed to register CoAP webservice {}", webserviceUri);
                            registeredResources.put(webserviceUri, false);
                        }
                        finally {
                            if(registeredResources.size() == webservices.size()){
                                log.info("Finished registration of {} resources from {}", webservices.size(), host);
                                registeredResourcesFuture.set(registeredResources.keySet());
                            }
                        }
                    }
                }, backendComponentFactory.getScheduledExecutorService());
            }
            catch (Exception e) {
                log.error("Error during CoAP Webservice registration.", e);
            }
        }
    }


    private void registerTubsResources(final SettableFuture<Set<URI>> registeredResourcesFuture,
                                       final Set<String> webservices, final String host){
        try{
            final URI rdfServiceUri = new URI("coap", null, host, -1, "/rdf", null, null);

            final SettableFuture<ExpiringModel> expiringModelFuture = SettableFuture.create();

            final Set<URI> registeredResources = new HashSet<>();

            expiringModelFuture.addListener(new Runnable(){
                @Override
                public void run() {
                    log.info("Received Model from {}", rdfServiceUri);
                    try {
                        ExpiringModel expiringModel = expiringModelFuture.get();
                        Map<URI, Model> models = ResourceToolbox.getModelsPerSubject(expiringModel.getModel());

                        for(String path : webservices){
                            final URI resourceUri = new URI("coap", null, host, -1, path, null, null);
                            log.debug("Lookup resource {} in response from {}", resourceUri, rdfServiceUri);

                            Model model = models.get(resourceUri);
                            if(model != null){
                                log.debug("Found resource {} in response from {}", resourceUri, rdfServiceUri);

                                registeredResources.add(resourceUri);

                                final ListenableFuture<URI> resourceRegistrationFuture =
                                        registerResource(resourceUri, model, expiringModel.getExpiry());

                                resourceRegistrationFuture.addListener(new Runnable(){
                                    @Override
                                    public void run() {
                                        try {
                                            URI resourceProxyUri = resourceRegistrationFuture.get();
                                            log.info("Successfully registered resource at {}", resourceProxyUri);
                                            startTubsObservation(resourceUri);
                                        }
                                        catch (Exception e) {
                                            log.error("Error during registration of resource {}.", resourceUri, e);
                                            registeredResourcesFuture.setException(e);
                                        }
                                    }
                                }, executorService);
                            }
                            else{
                                log.debug("There is no resource {} in response from {}", resourceUri, rdfServiceUri);
                            }
                        }

                        registeredResourcesFuture.set(registeredResources);
                    }
                    catch (Exception e) {
                        log.error("Exception while processing model from {}.", rdfServiceUri, e);
                        registeredResourcesFuture.setException(e);
                    }
                }
            }, executorService);

            RdfWebserviceResponseProcessor rdfResponseProcessor =
                    new RdfWebserviceResponseProcessor(expiringModelFuture, rdfServiceUri, executorService);

            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, rdfServiceUri);
            coapRequest.setAccept(MediaType.APP_SHDT, MediaType.APP_RDF_XML, MediaType.APP_N3, MediaType.APP_TURTLE);

            coapClientApplication.writeCoapRequest(coapRequest, rdfResponseProcessor);
        }
        catch(Exception e){
            log.error("Exception while registering TUBS resources.", e);
            registeredResourcesFuture.setException(e);
        }
    }

    private void startTubsObservation(URI resourceUri){
        try{
            URI observableWebserviceUri;
            if("/rdf".equals(resourceUri.getPath())){
                observableWebserviceUri =
                        new URI(resourceUri.getScheme(), null, resourceUri.getHost(), -1, "/location/_minimal", null, null);
            }
            else{
                observableWebserviceUri =
                        new URI(resourceUri.getScheme(), null, resourceUri.getHost(), -1,
                                resourceUri.getPath() + "/_minimal", null, null);
            }

            CoapWebserviceObserver observer =
                    new CoapWebserviceObserver(backendComponentFactory, observableWebserviceUri);

            observer.startObservation();
        }
        catch(Exception e){
            log.error("This should never happen!", e);
        }
    }
}

