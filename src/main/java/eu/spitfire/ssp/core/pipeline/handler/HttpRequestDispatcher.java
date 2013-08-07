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
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import eu.spitfire.ssp.core.webservice.ListOfServices;
import eu.spitfire.ssp.gateway.InternalRegisterTransparentGatewayMessage;
import eu.spitfire.ssp.gateway.ProxyServiceManager;
import eu.spitfire.ssp.gateway.InternalAbsoluteUriRequest;
import eu.spitfire.ssp.gateway.InternalRegisterServiceMessage;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;


/**
 * @author Oliver Kleine
 */
public class HttpRequestDispatcher extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	//Matches target URIs to request processor
	private Map<URI, HttpRequestProcessor> proxyServices;

    private HttpRequestProcessor transparentCoapGateway;

    private ExecutorService ioExecutorService;

    public HttpRequestDispatcher(ExecutorService ioExecutorService) throws Exception {
        this.ioExecutorService = ioExecutorService;

        this.proxyServices = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());
        //this.transparentGateways = Collections.synchronizedMap(new TreeMap<Integer, HttpRequestProcessor>());

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

        if(Main.SSP_HTTP_SERVER_PORT != 80)
            host += ":" + Main.SSP_HTTP_SERVER_PORT;

        URI targetUri = new URI("http://" + host + "/");
        registerService(targetUri, new ListOfServices(proxyServices.keySet()));
    }

    /**
     * Normalizes the given URI. It removes unnecessary slashes (/) or unnecessary parts of the path
     * (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following characters at the
     * end of the path and makes relative URIs absolute.
     *
     * @param uri The URI to be normalized
     */
	public URI normalizeURI(URI uri) {
        return uri.normalize();
    }

	/**
	 * Expected Message types:
	 * - HTTP Requests
	 */
	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me) throws Exception {

        if(!(me.getMessage() instanceof HttpRequest)) {
			super.messageReceived(ctx, me);
            return;
		}

        me.getFuture().setSuccess();
        final HttpRequest httpRequest = (HttpRequest) me.getMessage();

        //Create a future to wait for a response asynchronously
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        responseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                HttpResponse httpResponse;
                try {
                    httpResponse = responseFuture.get();

                    //Set HTTP CORS header to allow cross-site scripting
                    httpResponse.setHeader("Access-Control-Allow-Origin", "*");
                    httpResponse.setHeader("Access-Control-Allow-Credentials", "true");

                    log.debug("Write Response: {}.", httpResponse);
                } catch (Exception e) {
                    httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }

                ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse, me.getRemoteAddress());
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }, ioExecutorService);


        String httpTargetHost = httpRequest.getHeader(HttpHeaders.Names.HOST);
        String httpTargetPath = httpRequest.getUri();

        //Transparent proxy request or proxy service request?
        if(httpTargetPath.startsWith("http://" + httpTargetHost))
            handleTransparentProxyRequest(responseFuture, httpRequest);
        else
            handleProxyServiceRequest(responseFuture,httpRequest);
    }

    private void handleProxyServiceRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){
        try{
            URI targetUri = normalizeURI(new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri()));

            log.debug("Received HTTP request for " + targetUri);

            if(proxyServices.containsKey(targetUri)){
                log.info("HttpRequestProcessor found for {}", targetUri);

                HttpRequestProcessor httpRequestProcessor = proxyServices.get(targetUri);
                httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
            }
            else{
                log.warn("No HttpRequestProcessor found for {}. Send error response.", targetUri);
                HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.NOT_FOUND);

                responseFuture.set(httpResponse);
            }
        }
        catch(URISyntaxException e){
            responseFuture.set(HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
        }
    }

    private void handleTransparentProxyRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){
        try{
            URI targetUri = new URI(httpRequest.getUri());
            log.info("Received transparent proxy request for port {}.", targetUri.getPort());

            //Rewrite host and path of the HTTP request
            httpRequest.setHeader(HttpHeaders.Names.HOST,
                    targetUri.getPort() < 0 ? targetUri.getHost() : targetUri.getHost() + ":" + targetUri.getPort());
            httpRequest.setUri(targetUri.getPath());

            if(transparentCoapGateway != null){
                log.debug("HttpRequestProcessor found for port {}", targetUri.getPort());
                transparentCoapGateway.processHttpRequest(responseFuture, httpRequest);
            }
            else{
                HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.BAD_GATEWAY);

                responseFuture.set(httpResponse);
            }
        }
        catch (URISyntaxException e) {
            responseFuture.set(HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.info("Downstream: {}.", me.getMessage());

        if(me.getMessage() instanceof InternalRegisterServiceMessage){
            registerService((InternalRegisterServiceMessage) me.getMessage());
            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalAbsoluteUriRequest){
            retrieveAbsoluteUri((InternalAbsoluteUriRequest) me.getMessage());
            me.getFuture().setSuccess();
            return;
        }
        else if(me.getMessage() instanceof InternalRegisterTransparentGatewayMessage){
            registerTransparentGateway((InternalRegisterTransparentGatewayMessage) me.getMessage());
            me.getFuture().setSuccess();
            return;
        }

        ctx.sendDownstream(me);
    }

    private void registerTransparentGateway(InternalRegisterTransparentGatewayMessage message){
        this.transparentCoapGateway = message.getHttpRequestProcessor();
        log.info("Added transparent proxy/gateway for port: {}.", message.getPort());
    }

    private void retrieveAbsoluteUri(InternalAbsoluteUriRequest message){
        String proxyUriPath = message.getServicePath();

        //Add possibly contained target host of service origin to path part of proxy URI
        if(message.getTargetHostAddress() != null){
            InetAddress hostAddress = message.getTargetHostAddress();

            if(hostAddress instanceof Inet6Address)
                proxyUriPath = "/" + shortenInet6Address((Inet6Address) hostAddress) + proxyUriPath;
            else
                proxyUriPath = "/" + hostAddress + proxyUriPath;
        }

        //Add gateway prefix to path part of proxy URI
        proxyUriPath = "/" + message.getGatewayPrefix() + proxyUriPath;

        try {
            URI proxyUri = new URI("http",
                                   null,
                                   Main.SSP_DNS_NAME,
                                   Main.SSP_HTTP_SERVER_PORT == 80 ? -1 : Main.SSP_HTTP_SERVER_PORT,
                                   proxyUriPath,
                                   null,
                                   null);

            log.debug("Requested absolute URI: {}", proxyUri);
            message.getUriFuture().set(proxyUri);
        }
        catch (URISyntaxException e) {
            message.getUriFuture().setException(e);
        }
    }

    private void registerService(InternalRegisterServiceMessage message){
        registerService(message.getProxyUri(), message.getHttpRequestProcessor());
    }

    private void registerService(URI serviceURI, HttpRequestProcessor httpRequestProcessor){
        proxyServices.put(serviceURI, httpRequestProcessor);
        log.info("Registered new service: {}", serviceURI);
    }

    private String shortenInet6Address(Inet6Address inet6Address){
        String result = inet6Address.getHostAddress();

        //remove leading zeros per block
        result = result.replaceAll(":0000", ":0");
        result = result.replaceAll(":000", ":0");
        result = result.replaceAll(":00", ":0");
        result = result.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");

        //return shortened IP
        result = result.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
        log.debug("Shortened IPv6 address: {}", result);

        return result;
    }

//    private String formatInetAddress(InetAddress inetAddress){
//        if(inetAddress instanceof Inet6Address)
//            return formatInet6Address((Inet6Address) inetAddress);
//
//        if(inetAddress instanceof Inet4Address)
//            return formatInet4Address((Inet4Address) inetAddress);
//
//        return inetAddress.toString();
//    }
//
//    private String formatInet4Address(Inet4Address inet4Address) {
//        return inet4Address.getHostAddress().replaceAll(".", "-");
//    }
//
//    private String formatInet6Address(Inet6Address inet6Address){
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
//        return result.replaceAll(":", "-");
//    }

    public boolean unregisterService(String path, ProxyServiceManager backend) {
        //TODO
        return true;
    }
}


