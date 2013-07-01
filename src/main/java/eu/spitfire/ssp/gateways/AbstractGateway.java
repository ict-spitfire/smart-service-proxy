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
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link AbstractGateway} instance is a software component to enable a client that is capable of talking HTTP to
 * communicate with an arbitrary server. The {@link AbstractGateway} is responsible for translating the incoming
 * {@link HttpRequest} to whatever (proprietary) protocol the server talks and to return a suitable {@link HttpResponse}
 * which is then sent to the client.
 *
 * @author Oliver Kleine
 */
public abstract class AbstractGateway extends SimpleChannelHandler implements HttpRequestProcessor {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel internalChannel;
    private ExecutorService ioExecutorService;

    public AbstractGateway(LocalServerChannel internalChannel){
        this.internalChannel = internalChannel;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me){
        if(!(me.getMessage() instanceof HttpRequest)){
            ctx.sendUpstream(me);
            return;
        }



        me.getFuture().setSuccess();
    }

    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest){



        if(httpRequest.getUri().equals("/"))
            return processHttpRequestForUserInterface(httpRequest);
        else
            return processHttpRequestForBackendSpecificService(httpRequest);
    }

    public abstract HttpResponse processHttpRequestForBackendSpecificService(HttpRequest httpRequest);

    public abstract HttpResponse processHttpRequestForUserInterface(HttpRequest httpRequest);
}

