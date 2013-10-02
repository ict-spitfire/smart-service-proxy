package eu.spitfire.ssp.backends.generic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import eu.spitfire.ssp.backends.generic.exceptions.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.backends.generic.messages.InternalRegisterResourceMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.server.webservices.WebserviceForResourcesList;
import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class BackendResourceManager<T> extends SimpleChannelDownstreamHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Map<URI, T> resourceToDataOriginMap = new HashMap<>();
    private Multimap<T, URI> dataOriginToResourcesMultimap = HashMultimap.create();
    private Object monitor = new Object();

    private WebserviceForResourcesList<T> resourcesListWebservice;

    public BackendResourceManager(){
        this.resourcesListWebservice =  new WebserviceForResourcesList<T>(this.resourceToDataOriginMap) {};
    }


    @SuppressWarnings("unchecked")
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        if(me.getMessage() instanceof InternalRegisterResourceMessage){
            try{
                InternalRegisterResourceMessage<T> message = (InternalRegisterResourceMessage) me.getMessage();
                addResource(message.getResourceUri(), message.getDataOrigin());

                final URI resourceUri = message.getResourceUri();
                me.getFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if(!future.isSuccess())
                            removeResource(resourceUri);
                    }
                });
            }
            catch (ResourceAlreadyRegisteredException e) {
                me.getFuture().setFailure(e);
                return;
            }
        }

        else if(me.getMessage() instanceof InternalRemoveResourcesMessage){
            InternalRemoveResourcesMessage message = (InternalRemoveResourcesMessage) me.getMessage();
            removeResource(message.getResourceUri());
        }

        ctx.sendDownstream(me);
    }



    private void addResource(URI resourceUri, T dataOrigin) throws ResourceAlreadyRegisteredException {
        synchronized (monitor){
            if(!resourceToDataOriginMap.containsKey(resourceUri)){
                log.info("Registered new resource {} from data origin {}", resourceUri, dataOrigin);
                resourceToDataOriginMap.put(resourceUri, dataOrigin);
                dataOriginToResourcesMultimap.put(dataOrigin, resourceUri);
            }
            else if(dataOrigin.equals(resourceToDataOriginMap.get(resourceUri))){
                log.info("Resource {} was already registered from data origin {}", resourceUri, dataOrigin);
            }
            else{
                log.warn("Could not register resource {} from data origin {}.", resourceUri, dataOrigin);
                throw new ResourceAlreadyRegisteredException(resourceUri);
            }
        }
    }


    private void removeResource(URI resourceUri){
        synchronized (monitor){
            T dataOrigin = resourceToDataOriginMap.remove(resourceUri);
            if(dataOrigin != null){
                log.info("Removed registered resource {} from data origin {}.", resourceUri, dataOrigin);
                dataOriginToResourcesMultimap.remove(dataOrigin, resourceUri);
            }
        }
    }

    /**
     * Returns the data origin where the status of the given resource name is originally hosted
     * @param resourceUri the name of the resource
     * @return the data origin of the given resource
     */
    public final T getDataOrigin(URI resourceUri){
        return resourceToDataOriginMap.get(resourceUri);
    }

    public final Collection<URI> getResources(T dataOrigin){
        return dataOriginToResourcesMultimap.get(dataOrigin);
    }

    public WebserviceForResourcesList<T> getResourcesListWebservice(){
        return this.resourcesListWebservice;
    }

//    /**
//     * Retrieves an absolute resource proxy {@link URI} for the given service {@link URI}. The proxy resource URI is
//     * the URI that will be listed in the list of available proxy services.
//     *
//     * The originURI may be either absolute or relative, i.e. only contain path and possibly additionally query and/or
//     * fragment.
//     *
//     * If originURI is absolute the resource proxy URI will be like
//     * <code>http:<ssp-host>:<ssp-port>/?uri=resourceUri</code>. i.e. with the resourceUri in the query part of the
//     * resource proxy URI. If the resourceUri is relative, i.e. without scheme, host and port, the resource proxy URI will
//     * contain the path of the resourceUri in its path extended by a gateway prefix.
//     *
//     * @param resourceUri the {@link URI} of the origin (remote) service to get the resource proxy URI for.
//     */
//    public SettableFuture<URI> retrieveProxyUri(URI resourceUri){
//        //Create future
//        SettableFuture<URI> uriRequestFuture = SettableFuture.create();
//
//        //Send resource proxy URI request
//        InternalResourceProxyUriRequest proxyUriRequest =
//                new InternalResourceProxyUriRequest(uriRequestFuture, this.prefix, resourceUri);
//        Channels.write(this.localServerChannel, proxyUriRequest);
//
//        //return future
//        return uriRequestFuture;
//    }
}
