package eu.spitfire.ssp.backends.utils;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.InternalRegisterResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.InternalResourceProxyUriRequest;
import eu.spitfire.ssp.server.pipeline.messages.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A {@link DataOriginRegistry} is the component to register new data origins, i.e. the resources from data origins.
 * A data origin could e.g. be a Webservice whose response contains the status of at least one semantic resource. In
 * that example the generic type T would be an {@link URI}. Also, a data origin could be a local. Then the generic type
 * T could be {@link java.io.File} or {@link java.nio.file.Path}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOriginRegistry<T> extends ResourceStatusHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected DataOriginRegistry(BackendManager<T> backendManager) {
        super(backendManager);
    }

    /**
     * This method is to be called by implementing classes, i.e. registries for particular data origins,
     * to register the model from that data origin at the SSP.
     *
     * @param dataOrigin the data origin of the given model, e.g a file name or Webservice URI
     * @param requestProcessor the {@link eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor} which is
     *                         supposed to retrieve the status of a particular resource if the status was not cached
     *                         for any reason, e.g. unexpected expiry.
     * @return a {@link ListenableFuture} where {@link ListenableFuture#get()} returns the list of resource proxy URIs
     * for all resources from the given data origin / model.
     */
    public final ListenableFuture<Map<URI, Boolean>> registerResourcesFromDataOrigin(final T dataOrigin,
                                                                 final SemanticHttpRequestProcessor requestProcessor){

        final SettableFuture<Map<URI, Boolean>> resourceRegistrationFuture = SettableFuture.create();

        //Read model from data origin
        final SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture = SettableFuture.create();
        HttpRequest httpDummyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/dummy");
        this.backendManager.getDataOriginAccessory()
                           .processHttpRequest(dataOriginResponseFuture, httpDummyRequest, dataOrigin);

        //Wait for the model from data origin and register all contained resources
        final Map<URI, Boolean> resourcesRegistrationResult = Collections.synchronizedMap(new HashMap<URI, Boolean>());
        dataOriginResponseFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    final Model model = dataOriginResponseFuture.get().getModel();
                    final Date expiry = dataOriginResponseFuture.get().getExpiry();

                    final Map<URI, Model> resourcesFromDataOrigin = ResourceToolbox.getModelsPerSubject(model);

                    if (log.isInfoEnabled()) {
                        StringBuffer buffer = new StringBuffer();
                        buffer.append(resourcesFromDataOrigin.keySet().size());
                        buffer.append(" resources found at " + dataOrigin);
                        buffer.append(" (expiry: " + expiry + "):\n");

                        for (URI resourceUri : resourcesFromDataOrigin.keySet()) {
                            buffer.append(resourceUri + "\n");
                        }

                        log.info(buffer.toString());
                    }

                    //Register all semantic resources contained in the model from the data origin
                    for (final URI resourceUri : resourcesFromDataOrigin.keySet()) {

                        //Register resource for HTTP request dispatching (retrieve resource proxy URI)
                        final ListenableFuture<URI> resourceProxyUriFuture =
                                registerSemanticResource(resourceUri, requestProcessor);


                        //wait for the resource proxy URI retrieval to be finished
                        resourceProxyUriFuture.addListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    URI resourceProxyUri = resourceProxyUriFuture.get();

                                    log.debug("Proxy URI for resource {} is {}.", resourceUri, resourceProxyUri);

                                    //Send initial resource status to cache
                                    ChannelFuture initialCachingFuture = cacheResourceStatus(resourceUri,
                                            resourcesFromDataOrigin.get(resourceUri), expiry);

                                    initialCachingFuture.addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture initialCachingFuture)
                                                throws Exception {
                                            if (initialCachingFuture.isSuccess()) {
                                                resourcesRegistrationResult.put(resourceUri, true);
                                                backendManager.addResource(resourceUri, dataOrigin);
                                                log.info("Cached initial status of resource {} from data origin {}",
                                                        resourceUri, dataOrigin);
                                            } else {
                                                resourcesRegistrationResult.put(resourceUri, false);
                                                log.error("Could not cache initial status of resource {} from data" +
                                                        "origin {}", resourceUri, dataOrigin);
                                            }
                                        }
                                    });

                                } catch (InterruptedException e) {
                                    log.error("This should never happen.", e);
                                    resourcesRegistrationResult.put(resourceUri, false);
                                } catch (ExecutionException e) {
                                    if (e.getCause() instanceof ResourceAlreadyRegisteredException) {
                                        log.warn("Resource {} was already registered from data origin(s) {}",
                                                resourceUri, backendManager.getDataOrigin(resourceUri));
                                    }
                                    log.error("This should never happen.", e);
                                    resourcesRegistrationResult.put(resourceUri, false);
                                } finally {
                                    if (resourcesRegistrationResult.size() == resourcesFromDataOrigin.size()) {
                                        log.info("Registration of resources from {} finished.", dataOrigin);
                                        resourceRegistrationFuture.set(resourcesRegistrationResult);
                                    }
                                }
                            }
                        }, backendManager.getScheduledExecutorService());
                    }
                } catch (Exception e) {
                    log.error("Could not retrieve model from data origin!", e);
                    resourceRegistrationFuture.setException(e);
                }
            }
        }, backendManager.getScheduledExecutorService());

        return resourceRegistrationFuture;
    }

    /**
     * Method to be called by extending classes, i.e. instances of {@link BackendManager} whenever there is a new
     * webservice to be created on the smart service proxy, if the network behind this gateway is an IP enabled
     * network.
     *
     * @param resourceUri the (original/remote) {@link URI} of the new resource to be registered.
     * @param requestProcessor the {@link HttpRequestProcessor} instance to handle incoming requests for that resource
     *
     * @return the {@link com.google.common.util.concurrent.SettableFuture <URI>} containing the absolute {@link URI} for the newly registered
     * service or a {@link Throwable} if an error occurred during the registration process.
     */
    private ListenableFuture<URI> registerSemanticResource(URI resourceUri,
                                                          final SemanticHttpRequestProcessor requestProcessor){

        final SettableFuture<URI> resourceRegistrationFuture = SettableFuture.create();

        //Retrieve the URI the new service is available at on the proxy
        final SettableFuture<URI> proxyResourceUriFuture =  retrieveProxyUri(resourceUri);

        proxyResourceUriFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    //Register new service with the retrieved resource proxy URI
                    final URI resourceProxyUri = proxyResourceUriFuture.get();
                    ChannelFuture registrationFuture =
                            Channels.write(backendManager.getLocalServerChannel(),
                                    new InternalRegisterResourceMessage(resourceProxyUri, requestProcessor));

                    registrationFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess())
                                resourceRegistrationFuture.set(resourceProxyUri);
                            else
                                resourceRegistrationFuture.setException(channelFuture.getCause());
                        }
                    });

                } catch (InterruptedException e) {
                    log.error("Exception during service registration process.", e);
                    resourceRegistrationFuture.setException(e);

                } catch (ExecutionException e) {
                    log.warn("Exception during service registration process.", e.getMessage());
                    resourceRegistrationFuture.setException(e);
                }

            }
        }, backendManager.getScheduledExecutorService());

        return resourceRegistrationFuture;
    }

    /**
     * Retrieves an absolute resource proxy {@link URI} for the given service {@link URI}. The proxy resource URI is
     * the URI that will be listed in the list of available proxy services.
     *
     * The originURI may be either absolute or relative, i.e. only contain path and possibly additionally query and/or
     * fragment.
     *
     * If originURI is absolute the resource proxy URI will be like
     * <code>http:<ssp-host>:<ssp-port>/?uri=resourceUri</code>. i.e. with the resourceUri in the query part of the
     * resource proxy URI. If the resourceUri is relative, i.e. without scheme, host and port, the resource proxy URI will
     * contain the path of the resourceUri in its path extended by a gateway prefix.
     *
     * @param resourceUri the {@link URI} of the origin (remote) service to get the resource proxy URI for.
     */
    private SettableFuture<URI> retrieveProxyUri(URI resourceUri){
        //Create future
        SettableFuture<URI> uriRequestFuture = SettableFuture.create();

        //Send resource proxy URI request
        InternalResourceProxyUriRequest proxyUriRequest =
                new InternalResourceProxyUriRequest(uriRequestFuture, backendManager.getPrefix(), resourceUri);
        Channels.write(backendManager.getLocalServerChannel(), proxyUriRequest);

        //return future
        return uriRequestFuture;
    }
}
