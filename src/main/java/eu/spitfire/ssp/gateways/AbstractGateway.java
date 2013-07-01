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
package eu.spitfire.ssp.gateways;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.core.httpServer.webServices.HttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * A {@link AbstractGateway} instance is a software component to enable a client that is capable of talking HTTP to
 * communicate with an arbitrary server. The {@link AbstractGateway} is responsible for translating the incoming
 * {@link HttpRequest} to whatever (proprietary) protocol the server talks and to return a suitable {@link HttpResponse}
 * which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class AbstractGateway implements HttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel internalChannel;
    private ExecutorService ioExecutorService;

    private String prefix;
    private Map<URI, HttpRequestProcessor> services;

    public AbstractGateway(String prefix, LocalServerChannel internalChannel, ExecutorService ioExecutorService,
                           HttpRequestProcessor gui){
        this.prefix = prefix;
        this.internalChannel = internalChannel;
        this.ioExecutorService = ioExecutorService;
        this.services = Collections.synchronizedMap(new HashMap<URI, HttpRequestProcessor>());

        if(gui != null){
            registerService("/", gui);
        }
    }

    public AbstractGateway(String prefix, LocalServerChannel internalChannel, ExecutorService ioExecutorService){
        this(prefix, internalChannel, ioExecutorService, null);
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){


        URI targetUri = null;
        try {
            targetUri = new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri());
            log.debug("Received request for {}", targetUri);
        } catch (URISyntaxException e) {
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);

            httpResponse.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
            httpResponse.setContent(ChannelBuffers.wrappedBuffer(("" + e.getCause()).getBytes(Charset.forName("UTF-8"))));
            httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());

        }

        HttpRequestProcessor httpRequestProcessor = services.get(targetUri);
        httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
    }

    public void registerService(final String servicePath, final HttpRequestProcessor service){
        log.debug("Register service {} for {}.", servicePath, service);
        //Write registration request to Http Request Dispatcher
        final SettableFuture<URI> registrationFuture = SettableFuture.create();
        ChannelFuture writeFuture =
                Channels.write(internalChannel,
                        new InternalRegisterServiceMessage(registrationFuture, servicePath, this));

        registrationFuture.addListener(new Runnable() {
            @Override
            public void run() {
                URI serviceUri = null;
                try {
                    serviceUri = registrationFuture.get();
                } catch (InterruptedException e) {
                    log.error("This should never happen.", e);
                } catch (ExecutionException e) {
                    log.error("This should never happen.", e);
                }
                services.put(serviceUri, service);
                log.info("Registered new service {} for {}.", serviceUri, this.getClass().getName());
            }
        }, ioExecutorService);

        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) {
                log.info("Successfully written.");
            }
        });
    }

    public String getPrefix() {
        return prefix;
    }
}

