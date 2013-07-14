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
package eu.spitfire.ssp.gateway.coap;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.examples.performance.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import eu.spitfire.ssp.gateway.coap.noderegistration.CoapNodeRegistrationService;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author Oliver Kleine
 *
 */

public class CoapBackend implements HttpRequestProcessor {

    public static final int NODES_COAP_PORT = 5683;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private CoapServerApplication coapServer;
    private CoapClientApplication coapClient;

    public CoapBackend(){
        this.coapServer = new CoapServerApplication();
        coapServer.registerService(new CoapNodeRegistrationService(this));

        this.coapClient = new CoapClientApplication();
    }

    public void addService(InetSocketAddress remoteAddress, String servicePath){

    }

    public CoapClientApplication getCoapClient(){
        return this.coapClient;
    }


    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}