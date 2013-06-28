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
package eu.spitfire.ssp.backends.simple;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.vocabulary.VCARD;
import eu.spitfire.ssp.backends.ProprietaryGateway;
import eu.spitfire.ssp.core.Backend;
import eu.spitfire.ssp.core.httpServer.HttpRequestDispatcher;
import eu.spitfire.ssp.core.SelfDescription;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

/**
 * A {@link SimpleBackend} instance hosts a simple standard model. This backend is basicly to ensure the functionality
 * of the underlying handler stack. If it's instanciated (by setting <code>enableBackend="simple"</code> in the
 * <code>ssp.properties</code> file) it registers its WebService (/JohnSmith) at the {@link eu.spitfire.ssp.core.httpServer.HttpRequestDispatcher} instance which
 * causes this WebService to occur on the HTML page (at <code>http://<ssp-ip>:<ssp-port>/) listing the available webServices.
 *
 * @author Oliver Kleine
 *
 */

public class SimpleBackend extends ProprietaryGateway {

    private static Logger log = Logger.getLogger(SimpleBackend.class.getName());

    private HashMap<String, Model> resources = new HashMap<String, Model>();

    public SimpleBackend(HttpRequestDispatcher httpRequestDispatcher, String servicePathPrefix) {
        super(httpRequestDispatcher, servicePathPrefix);
    }


    private void registerService(){

        String servicePath = "http://example.org/JohnSmith";
        Model model = ModelFactory.createDefaultModel();
        model.createResource(servicePath).addProperty(VCARD.FN, "John Smith");

        resources.put(getServicePathPrefix() + "/JohnSmith", model);

        getHttpRequestDispatcher().

    }

    private void registerResources(){
        try {
            String personURI = "http://example.org/JohnSmith";
            Model model = ModelFactory.createDefaultModel();
            model.createResource(personURI).addProperty(VCARD.FN, "John Smith");

            resources.put(getServicePathPrefix() + "/JohnSmith", model);

            URI resourceTargetUri = new URI("http://"
                                    + HttpRequestDispatcher.SSP_DNS_NAME
                                    + ":" + HttpRequestDispatcher.SSP_HTTP_SERVER_PORT
                                    + prefix + "JohnSmith");


            HttpRequestDispatcher.getInstance().entityCreated(resourceTargetUri, this);

            if(log.isDebugEnabled()){
                log.debug("[SimpleBackend] Successfully added new resource at " + resourceTargetUri);
            }

        } catch (URISyntaxException e) {
            log.fatal("[SimpleBackend] This should never happen.", e);
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception{
        if(!(me.getMessage() instanceof HttpRequest)){
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest request = (HttpRequest) me.getMessage();
        Object response;

        //Look up resource
        String path = request.getUri();
        log.debug("Received request for path:" + path);

        for(String service : resources.keySet()){
            log.debug("Available Service: " + service);
        }

        Model model = resources.get(path);
            
        if(model != null){
            if(request.getMethod() == HttpMethod.GET){
                response = new SelfDescription(model, new URI(request.getUri()));
                
                log.debug("Resource found: " + path);
            }
            else{
                response = new DefaultHttpResponse(request.getProtocolVersion(),
                                                   HttpResponseStatus.METHOD_NOT_ALLOWED);

                log.debug("Method not allowed: " + request.getMethod());
            }
        }
        else{
            response = HttpResponseFactory.createHttpResponse(request.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);
            
            log.debug("Resource not found: " + path);
        }
       
        //Send response
        ChannelFuture future = Channels.write(ctx.getChannel(), response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public HttpResponse processHttpRequestForBackendSpecificService(HttpRequest httpRequest) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public HttpResponse processHttpRequestForUserInterface(HttpRequest httpRequest) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
