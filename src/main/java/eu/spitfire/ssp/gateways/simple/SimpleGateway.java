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
package eu.spitfire.ssp.gateways.simple;

import eu.spitfire.ssp.gateways.AbstractGateway;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.util.concurrent.ExecutorService;

/**
 * A {@link SimpleGateway} instance hosts a simple standard model. This backend is basicly to ensure the functionality
 * of the underlying handler stack. If it's instanciated (by setting <code>enableBackend="simple"</code> in the
 * <code>ssp.properties</code> file) it registers its WebService (/JohnSmith) at the {@link eu.spitfire.ssp.core.httpServer.HttpRequestDispatcher} instance which
 * causes this WebService to occur on the HTML page (at <code>http://<ssp-ip>:<ssp-port>/) listing the available webServices.
 *
 * @author Oliver Kleine
 *
 */

public class SimpleGateway extends AbstractGateway {

    private static Logger log = Logger.getLogger(SimpleGateway.class.getName());

    //private HashMap<String, Model> resources = new HashMap<String, Model>();

    public SimpleGateway(String prefix, LocalServerChannel internalChannel, ExecutorService ioExecutorService) {
        super(prefix, internalChannel, ioExecutorService);
        addService();
    }

    private void addService(){
        log.info("Add service /JohnSmith");
        registerService("/JohnSmith", new SimpleHttpRequestProcessor());
    }
}
