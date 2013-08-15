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
import eu.spitfire.ssp.server.pipeline.messages.InternalProxyUriRequest;
import eu.spitfire.ssp.server.pipeline.messages.InternalRegisterResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.InternalRemoveResourceMessage;
import eu.spitfire.ssp.server.pipeline.messages.ResourceAlreadyRegisteredException;
import eu.spitfire.ssp.server.webservices.FaviconHttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.ListOfRegisteredServices;
import eu.spitfire.ssp.proxyservicemanagement.*;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
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

	//Maps resource proxyservicemanagement uris to request processors
	private Map<URI, HttpRequestProcessor> proxyServices;

    private ExecutorService ioExecutorService;

    /**
     * @param ioExecutorService the {@link ExecutorService} providing the threads to send the responses
     * @throws Exception if some unexpected error occurs
     */
    public HttpRequestDispatcher(ExecutorService ioExecutorService) throws Exception {
        this.ioExecutorService = ioExecutorService;

        this.proxyServices = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());

        registerServiceForListOfServices();
        registerFavicon();
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
     * Registers the service to
     * @throws URISyntaxException
     */
    private void registerServiceForListOfServices(){
        try{
            //register service to provide list of available proxyServices
            if(Main.SSP_DNS_NAME == null)
               throw new RuntimeException("SSP_DNS_NAME must be defined! SSP_DNS_NAME can also be an IP address!");

            URI targetUri = new URI("http", null, Main.SSP_DNS_NAME,
                    Main.SSP_HTTP_PROXY_PORT == 80 ? -1 : Main.SSP_HTTP_PROXY_PORT , "/", null, null);

            registerResource(targetUri, new ListOfRegisteredServices(proxyServices.keySet()));
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

        //Create a future to wait for a response asynchronously
        final SettableFuture responseFuture = SettableFuture.create();
        responseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                //The object can either be an instance of ResourceStatusMessage, an Exception or a
                //HTTP response
                Object object;
                try {
                    object = responseFuture.get();
                    log.debug("Class of object: {}", object.getClass().getName());
                }
                catch(Exception e){
                    object = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);

                    ChannelBuffer payload =
                            ChannelBuffers.wrappedBuffer(e.getMessage().getBytes(Charset.forName("UTF-8")));
                    ((HttpResponse) object).setContent(payload);
                    ((HttpResponse) object).setHeader(HttpHeaders.Names.CONTENT_LENGTH, payload.readableBytes());
                }

                //Send object downstream
                ChannelFuture future = Channels.write(ctx.getChannel(), object, me.getRemoteAddress());
                future.addListener(ChannelFutureListener.CLOSE);
            }

        }, ioExecutorService);

        //Start processing of HTTP request
        handleProxyServiceRequest(responseFuture, httpRequest);
    }

    private void handleProxyServiceRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){
        try{
            URI targetUri = new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri());
            targetUri = targetUri.normalize();

            log.debug("Received HTTP request for " + targetUri);

            if(proxyServices.containsKey(targetUri)){
                log.info("HttpRequestProcessor found for {}", targetUri);

                HttpRequestProcessor httpRequestProcessor = proxyServices.get(targetUri);
                httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
            }
            else{
                log.warn("No HttpRequestProcessor found for {}. Send error response.", targetUri);
                responseFuture.set(HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.NOT_FOUND, targetUri.toString()));
            }
        }
        catch(URISyntaxException e){
            responseFuture.set(HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
        }
    }

    /**
     * This method is invoked by the netty framework for downstream messages. Messages other than
     * {@link InternalRegisterResourceMessage}, {@link InternalProxyUriRequest} and
     * {@link InternalRemoveResourceMessage} are just forwarded downstream without doing anything else.
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
        else if(me.getMessage() instanceof InternalProxyUriRequest){
            InternalProxyUriRequest message = (InternalProxyUriRequest) me.getMessage();
            URI resourceProxyUri = getProxyUri(message.getResourceUri());

            if(proxyServices.containsKey(resourceProxyUri)){
                message.getResourceProxyUriFuture()
                       .setException(new ResourceAlreadyRegisteredException(message.getResourceUri()));
                return;
            }

            message.getResourceProxyUriFuture().set(resourceProxyUri);

            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalRemoveResourceMessage){
            boolean removed = removeResource(((InternalRemoveResourceMessage) me.getMessage()).getResourceUri());
            if(!removed){
                me.getFuture().setFailure(new NullPointerException("There was no such service found!"));

                return;
            }

        }

        ctx.sendDownstream(me);
    }

    private boolean removeResource(URI resourceUri){
        URI resourceProxyUri = getProxyUri(resourceUri);
        if(proxyServices.remove(resourceProxyUri) != null){
            log.info("Successfully removed resource from list of registered services: {}.", resourceProxyUri);
            return true;
        }
        else{
            log.warn("Could not remove resource {}. (NOT FOUND)", resourceProxyUri);
            return false;
        }
    }


    private URI getProxyUri(URI resourceUri){
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

    private void registerResource(URI resourceProxyUri, HttpRequestProcessor httpRequestProcessor){
        proxyServices.put(resourceProxyUri, httpRequestProcessor);
        log.info("Registered new resource: {}", resourceProxyUri);
    }

    public boolean unregisterService(String path, AbstractProxyServiceManager backend) {
        //TODO
        return true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception{
        log.warn("Exception caught! Send Error Response.", e.getCause());

        HttpResponse httpResponse = HttpResponseFactory.createHttpErrorResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR, (Exception) e.getCause());

        Channels.write(ctx.getChannel(), httpResponse, ctx.getChannel().getRemoteAddress());
    }
}


