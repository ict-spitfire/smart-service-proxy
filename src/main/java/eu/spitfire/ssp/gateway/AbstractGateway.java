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
package eu.spitfire.ssp.gateway;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
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
import java.util.Map;
import java.util.Set;
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
    private ExecutorService executorService;

    private String prefix;
    private Map<URI, HttpRequestProcessor> services;

    /**
     *
     */
    protected AbstractGateway(String prefix){
        this.prefix = prefix;

        HttpRequestProcessor gui = this.getGui();
        if(gui != null)
            registerService("/", gui);
    }

    public abstract HttpRequestProcessor getGui();

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){
        try {
            URI targetUri = new URI("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri());
            log.debug("Received request for {}", targetUri);
            HttpRequestProcessor httpRequestProcessor = services.get(targetUri);
            httpRequestProcessor.processHttpRequest(responseFuture, httpRequest);
        }
        catch (URISyntaxException e) {
            HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);

            httpResponse.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
            httpResponse.setContent(ChannelBuffers.wrappedBuffer((""
                    + e.getCause()).getBytes(Charset.forName("UTF-8"))));
            httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());
        }
    }

    /**
     * Method to be called by extending classes, i.e. instances of {@link AbstractGateway} whenever there is a new
     * webservice to be created on the smart service proxy.
     *
     * @param servicePath the relative path of the webservice
     * @param requestProcessor the {@link HttpRequestProcessor} instance to handle the incoming request
     */
    protected void registerService(final String servicePath, final HttpRequestProcessor requestProcessor){
        log.debug("Register service {} for {}.", servicePath, requestProcessor);

        //Write registration request to Http Request Dispatcher
        final SettableFuture<URI> registrationFuture = SettableFuture.create();
        Channels.write(internalChannel, new InternalRegisterServiceMessage(registrationFuture, servicePath, this));

//        registrationFuture.addListener(new Runnable() {
//            @Override
//            public void run() {
//                URI serviceUri = null;
//                try {
//                    serviceUri = registrationFuture.get();
//                } catch (InterruptedException e) {
//                    log.error("This should never happen.", e);
//                } catch (ExecutionException e) {
//                    log.error("This should never happen.", e);
//                }
//                services.put(serviceUri, requestProcessor);
//                log.info("Registered new service {} for {}.", serviceUri, this.getClass().getName());
//            }
//        }, executorService);
    }

    public abstract void registerInitialServices();

    /**
     * Returns the specific prefix of this gateway. If wildcard DNS is enabled, then the prefix is used as the very
     * first element of the host part of all gateway specific service URIs. If wildcard DNS is disabled, then the
     * prefix is used as the very first path element of all gatew specific service URIs.
     *
     * @return the specific prefix of this gateway
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @param internalChannel the {@link Channel} to send internal messages to e.g. register or update services
     */
    public void setInternalChannel(LocalServerChannel internalChannel){
        this.internalChannel = internalChannel;
    }

    /**
     * @param executorService the thread-pool to handle gateway specific tasks, e.g. register or update services
     */
    public void setExecutorService(ExecutorService executorService){
        this.executorService = executorService;
    }

    public Set<URI> getServices(){
        return this.services.keySet();
    }
}

