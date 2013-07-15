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
import eu.spitfire.ssp.gateway.ProxyServiceCreator;
import eu.spitfire.ssp.gateway.InternalAbsoluteUriRequest;
import eu.spitfire.ssp.gateway.InternalRegisterServiceMessage;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
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

	//Matches target URIs to request processor (either local webwervice or gateway)
	private Map<URI, HttpRequestProcessor> services;

    private ExecutorService ioExecutorService;

    public HttpRequestDispatcher(ExecutorService ioExecutorService) throws Exception {
        this.ioExecutorService = ioExecutorService;

        registerServiceForListOfServices();
        registerFavicon();
    }

    private void registerFavicon() {
        //TODO
    }

    private void registerServiceForListOfServices() throws URISyntaxException {
        services = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());

        //register service to provide list of available services
        String host;
        if(Main.SSP_DNS_NAME != null)
            host = Main.SSP_DNS_NAME;
        else if(Main.DNS_WILDCARD_POSTFIX != null)
            host = "www." + Main.DNS_WILDCARD_POSTFIX;
        else
            throw new RuntimeException("At least one of SSP_DNS_NAME and DNS_WILDCARD_POSTFIX must be set." +
                    " SSP_DNS_NAME can be an IP address!");

        if(Main.SSP_HTTP_SERVER_PORT != 80)
            host += ":" + Main.SSP_HTTP_SERVER_PORT;

        URI targetUri = new URI("http://" + host + "/");
        registerService(targetUri, new ListOfServices(services.keySet()));
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

        URI targetUri = normalizeURI(new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri())) ;

        log.debug("Received HTTP request for " + targetUri);

        //Create a future to wait for a response asynchronously
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();

        responseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                HttpResponse httpResponse;
                try {
                   httpResponse = responseFuture.get();
                   log.debug("Write Response: {}.", httpResponse);
                } catch (Exception e) {
                    httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }

                ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse, me.getRemoteAddress());
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }, ioExecutorService);

        if(services.containsKey(targetUri)){
            log.info("HttpRequestProcessor found for {}", targetUri);

            HttpRequestProcessor httpRequestProcessor = services.get(targetUri);
            httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
        }
        else{
            log.warn("No HttpRequestProcessor found for {}. Send error response.", targetUri);
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);

            responseFuture.set(httpResponse);
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

        ctx.sendDownstream(me);
    }

    private void retrieveAbsoluteUri(InternalAbsoluteUriRequest message){

        String proxyUriHost;
        String proxyUriPath = message.getServicePath();

        if(Main.DNS_WILDCARD_POSTFIX == null){
            //Create host part of proxy URI
            proxyUriHost = Main.SSP_DNS_NAME;

            log.debug("1 {}", proxyUriPath);

            //Add possibly contained target host of service origin to path part of proxy URI
            if(message.getTargetHostAddress() != null)
                proxyUriPath = "/" + formatInetAddress(message.getTargetHostAddress()) + proxyUriPath;

            log.debug("2 {}", proxyUriPath);
            //Add gateway prefix to path part of proxy URI
            proxyUriPath = "/" + message.getGatewayPrefix() + proxyUriPath;
            log.debug("3 {}", proxyUriPath);
        }
        else{
            //Add gateway prefix to host part of proxy URI
            proxyUriHost = message.getGatewayPrefix() + "." + Main.DNS_WILDCARD_POSTFIX;

            //Add possibly contained target host of service origin to host part of proxy URI
            if(message.getTargetHostAddress() != null)
                proxyUriHost = formatInetAddress(message.getTargetHostAddress()) + "." + proxyUriHost;
        }

        if(Main.SSP_HTTP_SERVER_PORT != 80)
            proxyUriHost += ":" + Main.SSP_HTTP_SERVER_PORT;

        try {
            log.debug("Host: {}", proxyUriHost);
            URI proxyUri = URI.create("http://" + proxyUriHost + proxyUriPath);
            log.debug("Requested absolute URI: {}", proxyUri);
            message.getUriFuture().set(proxyUri);
        }
        catch (Exception e) {
            message.getUriFuture().setException(e);
        }
    }

    private void registerService(InternalRegisterServiceMessage message){
        registerService(message.getProxyUri(), message.getHttpRequestProcessor());
    }

    private void registerService(URI serviceURI, HttpRequestProcessor httpRequestProcessor){
        services.put(serviceURI, httpRequestProcessor);
        log.info("Registered new service: {}", serviceURI);
    }

    private String formatInetAddress(InetAddress inetAddress){
        if(inetAddress instanceof Inet6Address)
            return formatInet6Address((Inet6Address) inetAddress);

        if(inetAddress instanceof Inet4Address)
            return formatInet4Address((Inet4Address) inetAddress);

        return inetAddress.toString();
    }

    private String formatInet4Address(Inet4Address inet4Address) {
        return inet4Address.getHostAddress().replaceAll(".", "-");
    }

    private String formatInet6Address(Inet6Address inet6Address){
        String result = inet6Address.getHostAddress();
        
        //remove leading zeros per block
        result = result.replaceAll(":0000", ":0");
        result = result.replaceAll(":000", ":0");
        result = result.replaceAll(":00", ":0");
        result = result.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");

        //return shortened IP
        result = result.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
        log.debug("Shortened IPv6 address: {}", result);

        return result.replaceAll(":", "-");
    }

    public boolean unregisterService(String path, ProxyServiceCreator backend) {
        //TODO
        return true;
    }
}


