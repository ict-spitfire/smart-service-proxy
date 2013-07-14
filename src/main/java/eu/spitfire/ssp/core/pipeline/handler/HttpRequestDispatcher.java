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
import eu.spitfire.ssp.gateway.AbstractGateway;
import eu.spitfire.ssp.gateway.InternalRegisterServiceMessage;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
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
	private Map<URI, HttpRequestProcessor> httpRequestProcessors;

    private ExecutorService ioExecutorService;

    public HttpRequestDispatcher(ExecutorService ioExecutorService) throws Exception {
        this.ioExecutorService = ioExecutorService;
        httpRequestProcessors = Collections.synchronizedMap(new TreeMap<URI, HttpRequestProcessor>());

        //register service to provide list of available services
        URI targetUri;
        if(Main.DNS_WILDCARD_POSTFIX != null)
            targetUri = new URI("http://www." + Main.DNS_WILDCARD_POSTFIX + ":" + Main.SSP_HTTP_SERVER_PORT + "/");
        else
            targetUri = new URI("http://" + Main.SSP_DNS_NAME + ":" + Main.SSP_HTTP_SERVER_PORT + "/");

        log.info("Register list of services service at {}.", targetUri);

        registerService(targetUri, new ListOfServices(httpRequestProcessors.keySet()));
    }

    private void registerService(URI targetUri, HttpRequestProcessor httpRequestProcessor){
        httpRequestProcessors.put(targetUri, httpRequestProcessor);
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

//    /**
//     * Normalizes the given URI. It removes unnecessary slashes (/) or unnecessary parts of the path
//     * (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following characters at the
//     * end of the path and makes relative URIs absolute.
//     *
//     * @param uri The URI to be normalized
//     */
//	public URI normalizeURI(String uri) {
//		while(uri.substring(uri.length()-1).equals("#")) {
//			uri = uri.substring(0, uri.length()-1);
//		}
//
//        if(Main.DNS_WILDCARD_POSTFIX != null)
//            return new URI("core://" + uri(Main.DNS_WILDCARD_POSTFIX).resolve(uri).normalize();
//        else
//            return URI.create("core://")
//		return result;
//	}

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
        String host = httpRequest.getHeader("HOST");
        String port = host.substring(host.indexOf(":") + 1);
        host = host.substring(0, host.indexOf(":"));

        URI targetUri = new URI("http://" + host + ":" + port + httpRequest.getUri());
        log.info("Received request for service {}.", targetUri);

        targetUri = normalizeURI(new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri())) ;

        log.debug("Received HTTP request for " + targetUri);

//        if(httpRequest.getHeader("HOST").contains(Main.DNS_WILDCARD_POSTFIX)){
//            String targetUriHost = InetAddress.getByName(targetUri.getHost()).getHostAddress();
//            //remove leading zeros per block
//            targetUriHost = targetUriHost.replaceAll(":0000", ":0");
//            targetUriHost = targetUriHost.replaceAll(":000", ":0");
//            targetUriHost = targetUriHost.replaceAll(":00", ":0");
//            targetUriHost = targetUriHost.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");
//
//            //return shortened IP
//            targetUriHost = targetUriHost.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
//            log.debug("Target host: " + targetUriHost);
//
//            String targetUriPath = targetUri.getRawPath();
//            log.debug("Target path: " + targetUriPath);
//
//            if(IPAddressUtil.isIPv6LiteralAddress(targetUriHost)){
//                targetUriHost = "[" + targetUriHost + "]";
//            }
//
//            targetUri = normalizeURI(URI.create("core://" + targetUriHost + httpRequest.getUri()));
//            log.debug("Shortened target URI: " + targetUri);
//        }

        //Create a future to wait for a response asynchronously
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        responseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                HttpResponse httpResponse;
                try {
                   httpResponse = responseFuture.get();
                   log.debug("Write Response: {}.", httpRequest);
                } catch (Exception e) {
                    httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }

                ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse, me.getRemoteAddress());
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }, ioExecutorService);

        if(httpRequestProcessors.containsKey(targetUri)){
            HttpRequestProcessor httpRequestProcessor = httpRequestProcessors.get(targetUri);
            httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
        }
        else{
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);

            responseFuture.set(httpResponse);
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        log.info("Downstream: {}.", me.getMessage());
        if(me.getMessage() instanceof InternalRegisterServiceMessage){
            InternalRegisterServiceMessage registrationMessage = (InternalRegisterServiceMessage) me.getMessage();

            String prefix = registrationMessage.getGateway().getPrefix();
            String servicePath = registrationMessage.getServicePath();

            URI targetURI;
            if(Main.DNS_WILDCARD_POSTFIX != null)
                targetURI = new URI("http://" + prefix + "." + Main.DNS_WILDCARD_POSTFIX + ":"
                        + Main.SSP_HTTP_SERVER_PORT + servicePath);
            else
                targetURI = new URI("http://" + Main.SSP_DNS_NAME + ":" + Main.SSP_HTTP_SERVER_PORT
                        + "/" + registrationMessage.getGateway().getPrefix() + servicePath);

            targetURI = normalizeURI(targetURI);

            log.debug("Try to register new service {}.", targetURI);

            registerService(targetURI, registrationMessage.getGateway());

            registrationMessage.getRegistrationFuture().set(targetURI);
            //me.getFuture().setSuccess();
            return;
        }

        ctx.sendDownstream(me);
    }



    public boolean unregisterService(String path, AbstractGateway backend) {
        //TODO
        return true;
    }
}


