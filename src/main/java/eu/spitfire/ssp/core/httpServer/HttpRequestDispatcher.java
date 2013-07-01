/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
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
package eu.spitfire.ssp.core.httpServer;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.core.UIElement;
import eu.spitfire.ssp.core.httpServer.webServices.HttpRequestProcessor;
import eu.spitfire.ssp.core.httpServer.webServices.ListOfServices;
import eu.spitfire.ssp.gateways.AbstractGateway;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
        URI targetUri = new URI("http://www." + Main.DNS_WILDCARD_POSTFIX + ":" + Main.SSP_HTTP_SERVER_PORT + "/");
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
        return normalizeURI(uri.toString());
    }

    /**
     * Normalizes the given URI. It removes unnecessary slashes (/) or unnecessary parts of the path
     * (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following characters at the
     * end of the path and makes relative URIs absolute.
     *
     * @param uri The URI to be normalized
     */
	public URI normalizeURI(String uri) {
		while(uri.substring(uri.length()-1).equals("#")) {
			uri = uri.substring(0, uri.length()-1);
		}
		URI result = URI.create(Main.DNS_WILDCARD_POSTFIX).resolve(uri).normalize();
		return result;
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

        if(httpRequest.getHeader("HOST").contains(Main.DNS_WILDCARD_POSTFIX)){
            String targetUriHost = InetAddress.getByName(targetUri.getHost()).getHostAddress();
            //remove leading zeros per block
            targetUriHost = targetUriHost.replaceAll(":0000", ":0");
            targetUriHost = targetUriHost.replaceAll(":000", ":0");
            targetUriHost = targetUriHost.replaceAll(":00", ":0");
            targetUriHost = targetUriHost.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");

            //return shortened IP
            targetUriHost = targetUriHost.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
            log.debug("Target host: " + targetUriHost);

            String targetUriPath = targetUri.getRawPath();
            log.debug("Target path: " + targetUriPath);

            if(IPAddressUtil.isIPv6LiteralAddress(targetUriHost)){
                targetUriHost = "[" + targetUriHost + "]";
            }

            targetUri = normalizeURI(URI.create("http://" + targetUriHost + httpRequest.getUri()));
            log.debug("Shortened target URI: " + targetUri);
        }

        //Create a future to wait for a response asynchronously
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        responseFuture.addListener(new Runnable(){
            @Override
            public void run() {
                HttpResponse httpResponse;
                try {
                    httpResponse = responseFuture.get();
                } catch (Exception e) {
                    httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }

                ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse, me.getRemoteAddress());
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        log.info("Succesfully sent response to {}.", me.getRemoteAddress());
                    }
                });

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



	/**
	 * Return URIs for all known webServices.
	 */
	public Iterable<URI> getServices(){
		return httpRequestProcessors.keySet();
	}

	/**
	 * Will be called when an entity has been created.
	 * uri may be null in which case the return value will be a newly
	 * allocated URI for the entity.
	 */
	public URI registerService(AbstractGateway backend, String servicePath) throws URISyntaxException {

        URI serviceUri = new URI("http://www." + Main.SSP_DNS_NAME + ":" + Main.SSP_HTTP_SERVER_PORT + servicePath);
		serviceUri = normalizeURI(serviceUri);

        if(!(httpRequestProcessors.get(serviceUri) == backend)){
            httpRequestProcessors.put(serviceUri, backend);
            log.debug("New service created: " + serviceUri);
        }

		return serviceUri;
	}

//    public URI virtualEntityCreated(URI uri, AbstractGateway backend) {
//        uri = toThing(uri);
//        if(!(virtualEntities.get(uri) == backend)){
//            virtualEntities.put(uri, backend);
//            log.debug("New virtual entity created: " + uri);
//        }
//        return uri;
//    }

    public boolean entityDeleted(URI uri, AbstractGateway backend) {
        uri = toThing(uri);
        boolean succesful = httpRequestProcessors.remove(uri, backend);

        if(succesful){
            log.debug("Succesfully deleted " + uri);
        }
        else{
            log.debug("Could not delete: " + uri);
        }

        return succesful;
    }

    public boolean virtualEntityDeleted(URI uri, AbstractGateway backend) {
        uri = toThing(uri);
        boolean succesful = virtualEntities.remove(uri, backend);

        if(succesful){
            log.debug("Succesfully deleted " + uri);
        }
        else{
            log.debug("Could not delete: " + uri);
        }

        return succesful;
    }
	
	public AbstractGateway getBackend(String elementSE) {
		AbstractGateway b = httpRequestProcessors.get(elementSE);
		if(b == null) {
			URI uri = URI.create(SSP_DNS_NAME).resolve(elementSE).normalize();
			String path = uri.getRawPath();
			
			String pathPart = path.substring(0, path.indexOf("/"));
			b = backends.get(pathPart);
		}
		return b;
	}
}


