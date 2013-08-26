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
package eu.spitfire.ssp.gateways.simple;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.vocabulary.VCARD;
import eu.spitfire.ssp.gateways.AbstractGatewayManager;
import eu.spitfire.ssp.server.pipeline.handler.HttpRequestDispatcher;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.local.LocalServerChannel;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link SimpleGatewayManager} instance hosts a simple standard model. This backend is basicly to ensure the
 * functionality of the underlying handler stack. If it's instanciated (by setting
 * <code>enableProxyServiceManager="simple"</code> in the <code>ssp.properties</code> file) it registers its WebService
 * (http://example.org/JohnSmith) at the {@link HttpRequestDispatcher} which causes this WebService to occur on the
 * HTML page (at <code>core://<ssp-ip>:<ssp-port>/) listing the available webServices.
 *
 * It is a very simple example to show how to use the given functionality for further implementations of other
 * classes inheriting from {@link eu.spitfire.ssp.gateways.AbstractGatewayManager}.
 *
 * @author Oliver Kleine
 */

public class SimpleGatewayManager extends AbstractGatewayManager {

    private static Logger log = Logger.getLogger(SimpleGatewayManager.class.getName());

    public SimpleGatewayManager(String prefix, LocalServerChannel localChannel,
                                ScheduledExecutorService scheduledExecutorService) throws Exception{
        super(prefix, localChannel, scheduledExecutorService);
    }

    @Override
    public HttpRequestProcessor getGui() {
        //No GUI available
        return null;
    }

    @Override
    public void initialize(){
        try{
            Model model = ModelFactory.createDefaultModel();
            URI resourceUri = new URI("http", null, "example.org", -1, "/JohnSmith", null, null);
            model.createResource(resourceUri.toString()).addProperty(VCARD.FN, "John Smith");

            SemanticHttpRequestProcessor httpRequestProcessor = new SimpleHttpRequestProcessor(model);
            registerResource(resourceUri, httpRequestProcessor);
        }
        catch (Exception e){
            log.error("This should never happen.", e);
        }
    }

    @Override
    public void shutdown() {
        //Nothing to do here...
    }
}
