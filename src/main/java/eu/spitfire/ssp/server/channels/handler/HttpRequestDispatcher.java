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
package eu.spitfire.ssp.server.channels.handler;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.HttpSemanticProxyWebservice;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.exceptions.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
import eu.spitfire.ssp.backends.generic.messages.InternalRegisterDataOriginMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalRegisterWebserviceMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.server.channels.handler.cache.SemanticCache;
import eu.spitfire.ssp.server.webservices.*;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;


/**
 * The {@link HttpRequestDispatcher} is the topmost handler of the netty stack to receive incoming
 * HTTP requests. It contains a mapping from {@link URI} to {@link eu.spitfire.ssp.server.webservices.HttpWebservice} instances to
 * forward the request to the proper processor. *
 *
 * @author Oliver Kleine
 */
public class HttpRequestDispatcher extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	//Maps resource proxy uris to request processors
	private Map<URI, HttpWebservice> proxyServices;

    private String dnsName;
    private int httpProxyPort;
    
    private ExecutorService ioExecutorService;

    /**
     * @param ioExecutorService the {@link ExecutorService} providing the threads to send the responses
     * @throws Exception if some unexpected error occurs
     */
    public HttpRequestDispatcher(ExecutorService ioExecutorService, SemanticCache cache, Configuration config)
            throws Exception {

        this.ioExecutorService = ioExecutorService;
        this.dnsName = config.getString("SSP_HOST_NAME");
        this.httpProxyPort = config.getInt("SSP_HTTP_PORT");

        this.proxyServices = Collections.synchronizedMap(new HashMap<URI, HttpWebservice>());

        registerMainWebsite();
        registerFavicon();

        if(cache.supportsSPARQL())
            registerSparqlEndpoint(cache);
    }


    private void registerSparqlEndpoint(SemanticCache cache) {
        try{
            URI targetUri = new URI("http", null, this.dnsName,
                    this.httpProxyPort == 80 ? -1 : this.httpProxyPort , "/sparql", null, null);

            registerProxyWebservice(targetUri, new SparqlEndpoint(ioExecutorService, cache));
        }

        catch (URISyntaxException e) {
            log.error("This should never happen!", e);
        }
    }


    /**
     * This method is invoked by the netty framework for incoming messages from remote peers. It forwards
     * the incoming {@link HttpRequest} contained in the {@link MessageEvent} to the proper instance of
     * {@link eu.spitfire.ssp.server.webservices.HttpWebservice}, awaits its result asynchronously and sends the response to the client.
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
        URI resourceProxyUri = generateProxyWebserviceUri(new URI(httpRequest.getUri()));
        log.info("Received HTTP request for proxy Webservice {}", resourceProxyUri);

        //Lookup proper http request processor
        HttpWebservice httpWebservice = proxyServices.get(resourceProxyUri);

        //Send NOT FOUND if there is no proper processor
        if(httpWebservice == null){
            log.warn("No HttpWebservice found for {}. Send error response.", resourceProxyUri);
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND, resourceProxyUri.toString());
            writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
        }

        //For semantic services
        else if(httpWebservice instanceof HttpSemanticProxyWebservice){
            processRequestForRegisteredResource(ctx, (InetSocketAddress) me.getRemoteAddress(), httpRequest,
                    (HttpSemanticProxyWebservice) httpWebservice);
        }

        //For non-semantic services, e.g. GUIs
        else{
            processRequestForDefaultWebservice(ctx, (InetSocketAddress) me.getRemoteAddress(), httpRequest,
                    (HttpWebservice) httpWebservice);
        }
    }


    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.debug("Downstream: {}.", me.getMessage());

        if(me.getMessage() instanceof InternalRegisterWebserviceMessage){
            InternalRegisterWebserviceMessage message = (InternalRegisterWebserviceMessage) me.getMessage();

            URI webserviceUri = generateProxyWebserviceUri(message.getLocalUri());

            if(proxyServices.containsKey(webserviceUri)){
                me.getFuture().setFailure(new ResourceAlreadyRegisteredException(message.getLocalUri()));
                return;
            }

            registerProxyWebservice(webserviceUri, message.getHttpWebservice());
            me.getFuture().setSuccess();
            return;
        }

        else if(me.getMessage() instanceof InternalRegisterDataOriginMessage){
            InternalRegisterDataOriginMessage message = (InternalRegisterDataOriginMessage) me.getMessage();
            URI resourceUri = message.getDataOrigin().getGraphName();
            URI resourceProxyUri = generateProxyWebserviceUri(resourceUri);

            boolean success = registerProxyWebservice(resourceProxyUri, message.getHttpProxyWebservice());
            if(!success){
                me.getFuture().setFailure(new ResourceAlreadyRegisteredException(resourceUri));
                return;
            }
        }

        else if(me.getMessage() instanceof InternalRemoveResourcesMessage){
            InternalRemoveResourcesMessage removeResourceMessage = (InternalRemoveResourcesMessage) me.getMessage();
            URI resouceProxyUri = generateProxyWebserviceUri(removeResourceMessage.getResourceUri());

            if(unregisterProxyWebservice(resouceProxyUri))
                log.info("Removed {} from list of registered resources.", removeResourceMessage.getResourceUri());
            else
                log.error("Could not remove {}. Resource was not registered.", removeResourceMessage.getResourceUri());
        }

        ctx.sendDownstream(me);
    }


    private void processRequestForRegisteredResource(final ChannelHandlerContext ctx,
                                                     final InetSocketAddress remoteAddress,
                                                     final HttpRequest httpRequest,
                                                     HttpSemanticProxyWebservice httpRequestProcessor){

        final SettableFuture<WrappedDataOriginStatus> resourceResponseFuture = SettableFuture.create();

        resourceResponseFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    WrappedDataOriginStatus dataOriginStatus = resourceResponseFuture.get();
                    if(!dataOriginStatus.getStatus().isEmpty())
                        writeResourceResponseMessage(ctx.getChannel(), resourceResponseFuture.get(), remoteAddress);
                    else{
                        HttpResponse httpResponse =
                                HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                        HttpResponseStatus.OK, "Status changed.");

                        writeHttpResponse(ctx.getChannel(), httpResponse, remoteAddress);
                    }
                }
                catch (Exception e) {
                    HttpResponseStatus httpResponseStatus;

                    if(e instanceof ExecutionException && e.getCause() instanceof SemanticResourceException) {
                        SemanticResourceException exception = (SemanticResourceException) e.getCause();
                        httpResponseStatus = exception.getHttpResponseStatus();
                    }
                    else{
                        httpResponseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                    }

                    HttpResponse errorResponse =
                            HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                                                   httpResponseStatus,
                                                                   e);
                    writeHttpResponse(ctx.getChannel(), errorResponse, remoteAddress);
                }
            }
        }, ioExecutorService);

        httpRequestProcessor.processHttpRequest(resourceResponseFuture, httpRequest);
    }



    private void processRequestForDefaultWebservice(final ChannelHandlerContext ctx,
                                                    final InetSocketAddress remoteAddress,
                                                    final HttpRequest httpRequest,
                                                    HttpWebservice httpWebservice){

        final SettableFuture<HttpResponse> httpResponseFuture = SettableFuture.create();

        httpResponseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                try {
                    writeHttpResponse(ctx.getChannel(), httpResponseFuture.get(), remoteAddress);
                }
                catch (Exception e) {
                    HttpResponse httpResponse =
                            HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
                    writeHttpResponse(ctx.getChannel(), httpResponse, remoteAddress);
                }
            }
        }, ioExecutorService);

        httpWebservice.processHttpRequest(httpResponseFuture, httpRequest);
    }

    /**
     * Registers the service to provide the favicon.ico
     */
    private void registerFavicon() throws URISyntaxException {
        URI faviconUri = new URI("http", null, this.dnsName, this.httpProxyPort, "/favicon.ico",null, null);
        registerProxyWebservice(faviconUri, new FaviconHttpWebservice());
    }

    /**
     * Registers the service to provide a list of registered services
     */
    private void registerMainWebsite() throws URISyntaxException {
        //register service to provide list of available proxyServices
        if(this.dnsName == null)
            throw new RuntimeException("SSP_HOST_NAME must be defined (DNS name or IP address)!");

        URI websiteUri = new URI("http", null, this.dnsName,
                this.httpProxyPort == 80 ? -1 : this.httpProxyPort , "/", null, null);

        registerProxyWebservice(websiteUri, new ProxyMainWebsite(proxyServices));
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
    }



    private void writeResourceResponseMessage(Channel channel, WrappedDataOriginStatus dataOriginStatus,
                                              InetSocketAddress remoteAddress){

        ChannelFuture future = Channels.write(channel, dataOriginStatus, remoteAddress);
        future.addListener(ChannelFutureListener.CLOSE);
    }



    private URI generateProxyWebserviceUri(URI uri) throws URISyntaxException {
        if(uri.isAbsolute())
            return new URI("http", null, this.dnsName,
                                    this.httpProxyPort == 80 ? -1 : this.httpProxyPort, "/",
                                    "uri=" + uri.toString(), null);

        else
            return new URI("http", null, this.dnsName,
                                    this.httpProxyPort == 80 ? -1 : this.httpProxyPort,
                                    uri.getPath(), uri.getQuery(), uri.getFragment());
    }



    private synchronized boolean registerProxyWebservice(URI proxyWebserviceUri,
                                                         HttpWebservice httpWebservice){
        if(proxyServices.containsKey(proxyWebserviceUri))
            return false;

        proxyServices.put(proxyWebserviceUri, httpWebservice);
        log.info("Registered new Webservice: {}", proxyWebserviceUri);
        return true;
    }


    private boolean unregisterProxyWebservice(URI proxyWebserviceUri){
        if(proxyServices.remove(proxyWebserviceUri) != null){
            log.info("Successfully removed resource from list of registered services: {}.", proxyWebserviceUri);
            return true;
        }
        else{
            log.warn("Could not remove resource {}. (NOT FOUND)", proxyWebserviceUri);
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


