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
package eu.spitfire.ssp.core.pipeline.handler;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.core.InternalProxyUriRequest;
import eu.spitfire.ssp.core.InternalRegisterResourceMessage;
import eu.spitfire.ssp.core.InternalRemoveResourceMessage;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import eu.spitfire.ssp.core.webservice.ListOfRegisteredServices;
import eu.spitfire.ssp.gateway.*;
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
 * @author Oliver Kleine
 */
public class HttpRequestDispatcher extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	//Maps resource proxy uris to request processors
	private Map<URI, HttpRequestProcessor> proxyServices;

    private ExecutorService ioExecutorService;

    public HttpRequestDispatcher(ExecutorService ioExecutorService) throws Exception {
        this.ioExecutorService = ioExecutorService;

        this.proxyServices = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());

        registerServiceForListOfServices();
        registerFavicon();
    }

    private void registerFavicon() {
        //TODO
    }

    private void registerServiceForListOfServices() throws URISyntaxException {
        //register service to provide list of available proxyServices
        String host;
        if(Main.SSP_DNS_NAME != null)
            host = Main.SSP_DNS_NAME;
        else
            throw new RuntimeException("SSP_DNS_NAME must be defined! SSP_DNS_NAME can also be an IP address!");

        if(Main.SSP_HTTP_PROXY_PORT != 80)
            host += ":" + Main.SSP_HTTP_PROXY_PORT;

        URI targetUri = new URI("http://" + host + "/");
        registerResource(targetUri, new ListOfRegisteredServices(proxyServices.keySet()));
    }

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

//                    if(object instanceof HttpResponse){
//                        ((HttpResponse) object).setHeader("Access-Control-Allow-Origin", "*");
//                        ((HttpResponse) object).setHeader("Access-Control-Allow-Credentials", "true");
//                    }

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
        handleProxyServiceRequest(responseFuture,httpRequest);
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

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.info("Downstream: {}.", me.getMessage());

        if(me.getMessage() instanceof InternalRegisterResourceMessage){
            InternalRegisterResourceMessage message = (InternalRegisterResourceMessage) me.getMessage();
            registerResource(message.getResourceProxyUri(), message.getHttpRequestProcessor());
            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalProxyUriRequest){
            InternalProxyUriRequest internalProxyUriRequest = (InternalProxyUriRequest) me.getMessage();
            URI proxyUri = getProxyUri(internalProxyUriRequest.getResourceUri());
            internalProxyUriRequest.getResourceProxyUriFuture().set(proxyUri);

            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalRemoveResourceMessage){
            removeResource(((InternalRemoveResourceMessage) me.getMessage()).getResourceUri());
        }

        ctx.sendDownstream(me);
    }

    private void removeResource(URI resourceUri){
        URI proxyURI = getProxyUri(resourceUri);
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

//    private String shortenInet6Address(Inet6Address inet6Address){
//        String result = inet6Address.getHostAddress();
//
//        //remove leading zeros per block
//        result = result.replaceAll(":0000", ":0");
//        result = result.replaceAll(":000", ":0");
//        result = result.replaceAll(":00", ":0");
//        result = result.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");
//
//        //return shortened IP
//        result = result.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
//        log.debug("Shortened IPv6 address: {}", result);
//
//        return result;
//    }

    public boolean unregisterService(String path, ProxyServiceManager backend) {
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


