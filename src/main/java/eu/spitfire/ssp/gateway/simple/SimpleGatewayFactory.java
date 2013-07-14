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
package eu.spitfire.ssp.gateway.simple;

import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import eu.spitfire.ssp.gateway.AbstractGateway;
import org.apache.log4j.Logger;

/**
 * A {@link SimpleGatewayFactory} instance hosts a simple standard model. This backend is basicly to ensure the functionality
 * of the underlying handler stack. If it's instanciated (by setting <code>enableBackend="simple"</code> in the
 * <code>ssp.properties</code> file) it registers its WebService (/JohnSmith) at the {@link eu.spitfire.ssp.core.pipeline.handler.HttpRequestDispatcher} instance which
 * causes this WebService to occur on the HTML page (at <code>core://<ssp-ip>:<ssp-port>/) listing the available webServices.
 *
 * @author Oliver Kleine
 *
 */

public class SimpleGatewayFactory extends AbstractGateway {

    private static Logger log = Logger.getLogger(SimpleGatewayFactory.class.getName());

    public SimpleGatewayFactory(String prefix) {
        super(prefix);
    }

    @Override
    public HttpRequestProcessor getGui() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void registerInitialServices(){
        log.info("Add service /JohnSmith");
        registerService("/JohnSmith", new SimpleHttpRequestProcessor());
    }
}
