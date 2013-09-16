/**
 * Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire.ssp.server.pipeline.handler;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.server.pipeline.handler.cache.AbstractSemanticCache;
import eu.spitfire.ssp.server.pipeline.messages.*;
import eu.spitfire.ssp.server.webservices.*;
import eu.spitfire.ssp.backends.*;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;


/**
 * The {@link HttpRequestDispatcher} is the topmost handler of the netty stack to receive incoming
 * HTTP requests. It contains a mapping from {@link URI} to {@link HttpRequestProcessor} instances to
 * forward the request to the proper processor. *
 *
 * @author Oliver Kleine
 */
public class HttpRequestDispatcher extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	//Maps resource proxy uris to request processors
	private Map<URI, HttpRequestProcessor> proxyServices;

    private ExecutorService ioExecutorService;

    /**
     * @param ioExecutorService the {@link ExecutorService} providing the threads to send the responses
     * @throws Exception if some unexpected error occurs
     */
    public HttpRequestDispatcher(ExecutorService ioExecutorService, boolean enableSparqlEndpoint,
                                 AbstractSemanticCache cache) throws Exception {
        this.ioExecutorService = ioExecutorService;

        this.proxyServices = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());

        registerServiceForListOfServices();
        registerFavicon();

        if(enableSparqlEndpoint)
            registerSparqlEndpoint(cache);
    }

    private void registerSparqlEndpoint(AbstractSemanticCache cache) {
        try{
            URI targetUri = new URI("http", null, Main.SSP_DNS_NAME,
                    Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT , "/sparql", null, null);

            registerResource(targetUri, new SparqlEndpoint(ioExecutorService, cache));
        } catch (URISyntaxException e) {
            log.error("This should never happen!", e);
        }
    }

    /**
     * This method is invoked by the netty framework for incoming messages from remote peers. It forwards
     * the incoming {@link HttpRequest} contained in the {@link MessageEvent} to the proper instance of
     * {@link HttpRequestProcessor}, awaits its result asynchronously and sends the response to the client.
     *
     * @param ctx the {@link ChannelHandlerContext} to link actual task to a {@link Channel}
     * @param me the {@link MessageEvent} containing the {@link HttpRequest}
     * @throws Exception
     */
	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me) throws Exception {

        if(!(me.getMessage() instanceof HttpRequest)) {
			ctx.sendUpstream(me);
            return;
		}

        me.getFuture().setSuccess();
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        //Create resource proxy uri from request
        URI resourceProxyUri;
        try{
            resourceProxyUri = new URI(httpRequest.getUri());
            if(!resourceProxyUri.isAbsolute()){
                resourceProxyUri = new URI("http", null, Main.SSP_DNS_NAME,
                        Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT, resourceProxyUri.getPath(),
                        resourceProxyUri.getQuery(), resourceProxyUri.getFragment());
            }
            resourceProxyUri = resourceProxyUri.normalize();
            log.info("Received HTTP request for " + resourceProxyUri);
        }
        catch(URISyntaxException e){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.BAD_REQUEST, e);
            writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
            return;
        }

        //Lookup proper http request processor
        HttpRequestProcessor httpRequestProcessor = proxyServices.get(resourceProxyUri);

        //Send NOT FOUND if there is no proper processor
        if(httpRequestProcessor == null){
            log.warn("No HttpRequestProcessor found for {}. Send error response.", resourceProxyUri);
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND, resourceProxyUri.toString());
            writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
            return;
        }

        //For non-semantic services, e.g. GUIs
        if(httpRequestProcessor instanceof DefaultHttpRequestProcessor){
            final SettableFuture<HttpResponse> httpResponseFuture = SettableFuture.create();
            httpResponseFuture.addListener(new Runnable(){

                @Override
                public void run() {
                    try {
                        writeHttpResponse(ctx.getChannel(), httpResponseFuture.get(),
                                (InetSocketAddress) me.getRemoteAddress());
                    }
                    catch (Exception e) {
                        HttpResponse httpResponse =
                                HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
                        writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
                    }
                }
            }, ioExecutorService);

            log.info("Http Request Processor: {}", httpRequestProcessor.getClass().getName());
            httpRequestProcessor.processHttpRequest(httpResponseFuture, httpRequest);
        }

        //For semantic services
        else if(httpRequestProcessor instanceof SemanticHttpRequestProcessor){
            final SettableFuture<ResourceStatusMessage> resourceStatusFuture = SettableFuture.create();

            resourceStatusFuture.addListener(new Runnable(){
                @Override
                public void run() {
                    try {
                        writeResourceStatusMessage(ctx.getChannel(), resourceStatusFuture.get(),
                                (InetSocketAddress) me.getRemoteAddress());
                    }
                    catch (InterruptedException e) {
                        HttpResponse httpResponse =
                                HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
                        writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
                    }
                    catch (ExecutionException e) {
                        HttpResponse httpResponse;
                        if(e.getCause() instanceof ProxyServiceException){

                            ProxyServiceException exception = (ProxyServiceException) e.getCause();
                            if(exception.getHttpResponseStatus() == HttpResponseStatus.GATEWAY_TIMEOUT){
                                URI resourceProxyUri = generateResourceProxyUri(exception.getResourceUri());
                                log.info("Request for resource {} timed out. Remove!", resourceProxyUri);
                                unregisterResourceProxyUri(resourceProxyUri);
                            }

                            httpResponse =
                                    HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                            exception.getHttpResponseStatus(), exception.getMessage());
                        }
                        else{
                            httpResponse =
                                    HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
                        }

                        writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
                    }
                }
            }, ioExecutorService);

            httpRequestProcessor.processHttpRequest(resourceStatusFuture, httpRequest);
        }
    }

    /**
     * This method is invoked by the netty framework for downstream messages. Messages other than
     * {@link InternalRegisterResourceMessage}, {@link eu.spitfire.ssp.server.pipeline.messages.InternalResourceProxyUriRequest} and
     * {@link eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourcesMessage} are just forwarded downstream without doing anything else.
     *
     * @param ctx the {@link ChannelHandlerContext} to link the method invokation with a {@link Channel}
     * @param me the {@link MessageEvent} containing the message
     *
     * @throws Exception
     */
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.debug("Downstream: {}.", me.getMessage());

        if(me.getMessage() instanceof InternalRegisterResourceMessage){
            InternalRegisterResourceMessage message = (InternalRegisterResourceMessage) me.getMessage();

            if(proxyServices.containsKey(message.getResourceProxyUri())){
                me.getFuture().setFailure(new ResourceAlreadyRegisteredException(message.getResourceProxyUri()));
                return;
            }

            registerResource(message.getResourceProxyUri(), message.getHttpRequestProcessor());
            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalResourceProxyUriRequest){
            InternalResourceProxyUriRequest message = (InternalResourceProxyUriRequest) me.getMessage();
            URI resourceUri = message.getResourceUri();

            URI resourceProxyUri;
            if(resourceUri.isAbsolute())
                resourceProxyUri = generateResourceProxyUri(message.getResourceUri());
            else
                resourceProxyUri = generateResourceProxyUri(new URI("/" + message.getGatewayPrefix() +
                    message.getResourceUri().toString()));

            if(proxyServices.containsKey(resourceProxyUri)){
                message.getResourceProxyUriFuture()
                        .setException(new ResourceAlreadyRegisteredException(message.getResourceUri()));
                return;
            }

            message.getResourceProxyUriFuture().set(resourceProxyUri);

            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalRemoveResourcesMessage){
            InternalRemoveResourcesMessage removeResourceMessage = (InternalRemoveResourcesMessage) me.getMessage();
            boolean removed = unregisterResourceUri(removeResourceMessage.getResourceUri());
            if(removed)
                log.info("Removed {} from list of registered resources.", removeResourceMessage.getResourceUri());
            else
                log.error("Could not remove {}. Resource was not registered.", removeResourceMessage.getResourceUri());
        }

        ctx.sendDownstream(me);
    }

    /**
     * Registers the service to provide the favicon.ico
     */
    private void registerFavicon(){
        try {
            registerResource(new URI("http", null, Main.SSP_DNS_NAME, Main.SSP_HTTP_PROXY_PORT, "/favicon.ico", null, null),
                    new FaviconHttpRequestProcessor());
        } catch (URISyntaxException e) {
            log.error("This should never happen.", e);
        }
    }

    /**
     * Registers the service to provide a list of registered services
     */
    private void registerServiceForListOfServices(){
        try{
            //register service to provide list of available proxyServices
            if(Main.SSP_DNS_NAME == null)
                throw new RuntimeException("SSP_DNS_NAME must be defined! SSP_DNS_NAME can also be an IP address!");

            URI targetUri = new URI("http", null, Main.SSP_DNS_NAME,
                    Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT , "/", null, null);

            registerResource(targetUri, new ListOfRegisteredServices(proxyServices));
        } catch (URISyntaxException e) {
            log.error("This should never happen!", e);
        }
    }

    /**
     * Sends an HTTP response to the given remote address
     *
     * @param channel the {@link Channel} to send the response over
     * @param httpResponse the {@link HttpResponse} to be sent
     * @param remoteAddress the recipient of the response
     */
    private void writeHttpResponse(Channel channel, final HttpResponse httpResponse,
                                   final InetSocketAddress remoteAddress){
        ChannelFuture future = Channels.write(channel, httpResponse, remoteAddress);
        future.addListener(ChannelFutureListener.CLOSE);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.debug("Response sent to {} (Status: {})", remoteAddress, httpResponse.getStatus());
            }
        });
    }

    /**
     * Sends a {@link ResourceStatusMessage} on the given channel which is converted into an HTTP response after
     * caching.
     *
     * @param channel the {@link Channel} to send the message over
     * @param resourceStatusMessage the message containing the information to be sent
     * @param remoteAddress the recipient of the eventual HTTP response (which is generated from the given
     *                      {@link ResourceStatusMessage}
     */
    private void writeResourceStatusMessage(Channel channel, final ResourceStatusMessage resourceStatusMessage,
                                            final InetSocketAddress remoteAddress){
        ChannelFuture future = Channels.write(channel, resourceStatusMessage, remoteAddress);
        future.addListener(ChannelFutureListener.CLOSE);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.debug("Resource status sent to {}.", remoteAddress);
            }
        });
    }

    /**
     * Returns the proper absolute resource proxy uri for the given resource uri
     *
     * @param resourceUri the {@link URI} to get the proxy resource uri for
     *
     * @return the proper absolute resource proxy uri for the given resource uri or null if an error occurred.
     */
    private URI generateResourceProxyUri(URI resourceUri){
        try{
            if(resourceUri.isAbsolute()){
                return new URI("http", null, Main.SSP_DNS_NAME,
                                        Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT, "/",
                                        "uri=" + resourceUri.toString(), null);
            }
            else{
                return new URI("http", null, Main.SSP_DNS_NAME,
                                        Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT,
                                        resourceUri.getPath(), resourceUri.getQuery(), resourceUri.getFragment());
            }
        }
        catch(URISyntaxException e){
            log.error("This should never happen!", e);
            return null;
        }
    }

    /**
     * Registers a new resource with its appropriate {@link HttpRequestProcessor}. Upon invokation of this method
     * the resource proxy uri is contained in the list of registered services.
     *
     * @param resourceProxyUri the resource proxy uri to be registered
     * @param httpRequestProcessor the {@link HttpRequestProcessor} to handle incoming requests for the resource proxy uri
     */
    private void registerResource(URI resourceProxyUri, HttpRequestProcessor httpRequestProcessor){
        proxyServices.put(resourceProxyUri, httpRequestProcessor);
        log.info("Registered new resource: {}", resourceProxyUri);
    }

    /**
     * Removes the resource proxy uri representing the given resource uri from the list of registered resources
     *
     * @param resourceUri the {@link URI} identifying the resource to be removed
     *
     * @return <code>true</code> if the resource was deleted successfully, <code>false</code> if the resource could
     * not be deleted or if there was no such resource registered.
     */
    private boolean unregisterResourceUri(URI resourceUri){
        URI resourceProxyUri = generateResourceProxyUri(resourceUri);
        return unregisterResourceProxyUri(resourceProxyUri);
    }

    /**
     * Removes the resource proxy uri from the list of registered resources
     *
     * @param resourceProxyUri the {@link URI} identifying the resource to be removed
     *
     * @return <code>true</code> if the resource was deleted successfully, <code>false</code> if the resource could
     * not be deleted or if there was no such resource registered.
     */
    private boolean unregisterResourceProxyUri(URI resourceProxyUri){
        if(proxyServices.remove(resourceProxyUri) != null){
            log.info("Successfully removed resource from list of registered services: {}.", resourceProxyUri);
            return true;
        }
        else{
            log.warn("Could not remove resource {}. (NOT FOUND)", resourceProxyUri);
            return false;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception{

        if(ctx.getChannel().isConnected()){
            log.warn("Exception caught! Send Error Response.", e.getCause());

            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, (Exception) e.getCause());

            writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) ctx.getChannel().getRemoteAddress());
        }
        else{
            log.warn("Exception on an unconnected channel! IGNORE.", e.getCause());
            ctx.getChannel().close();
        }
    }
}


