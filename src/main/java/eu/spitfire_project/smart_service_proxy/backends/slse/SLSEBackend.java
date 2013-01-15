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
package eu.spitfire_project.smart_service_proxy.backends.slse;

import com.google.common.io.Files;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire_project.smart_service_proxy.core.Answer;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.UIElement;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.io.File;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * @author Henning Hasemann
 * @author Oliver Kleine
 */
public class SLSEBackend extends Backend {

	private final ElementSemanticEntityCache eseCache;
	private final ServiceLevelSemanticEntityCache slseCache;
	private final ServiceLevelSemanticEntityBuilder slseBuilder;
    private boolean waitForPolling;

    private static byte[] htmlContent;
    static{
        //read html file,
        try{
            Charset charset = Charset.forName("UTF-8");
            String s = Files.toString(new File("data/slse/ui/create_entity_form.html"), charset );
            htmlContent = s.getBytes(charset);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SLSEBackend(boolean waitForPolling, boolean pollParallel, int pollInterval) {
		eseCache = new ElementSemanticEntityCache(this, pollParallel, pollInterval);
        this.waitForPolling = waitForPolling;
		slseCache = new ServiceLevelSemanticEntityCache(this, waitForPolling);
		slseBuilder = new ServiceLevelSemanticEntityBuilder(eseCache, slseCache, this);
	}
	
	@Override
	public void bind() {
		super.bind();
		assert(EntityManager.getInstance() != null);
		addProxy("http://" + EntityManager.SSP_DNS_NAME + ":" + EntityManager.SSP_HTTP_SERVER_PORT);
	}
	
	public void addProxy(String uri) {
		eseCache.addProxy(uri);
	}

	@Override
	public Collection<UIElement> getUIElements() {
		Vector<UIElement> elements = new Vector<UIElement>();
		elements.add(new UIElement("Create SE", EntityManager.getInstance().normalizeURI(prefix + "/create-entity")));
		return elements;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		if(!(e.getMessage() instanceof HttpRequest)){
			super.messageReceived(ctx, e);
		}

		HttpRequest httpRequest = (HttpRequest) e.getMessage();
		//URI uri = EntityManager.getInstance().normalizeURI(EntityManager.SSP_DNS_NAME + httpequest.getUri());
		URI targetUri = EntityManager.getInstance().normalizeURI(URI.create("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri()));

		String targetUriHost = Inet6Address.getByName(targetUri.getHost()).getHostAddress();
		String targetUriPath = targetUri.getRawPath();

		System.out.println("# received request: " + targetUriPath + " prefix=" + prefix +
				" normed create uri=" + EntityManager.getInstance().normalizeURI(prefix + "/create-entity").toString() +
				" eq=" + targetUriPath.equals(EntityManager.getInstance().normalizeURI(prefix + "/create-entity").toString()));
        
		/*
		 * TODO:
		 * - if GET "$base/entities/create", return entity creation form
		 * - if GET "$base/entities/" || GET "$base/.well-known/servers" return entity list
		 * - if POST "$base/entities/", call createEntity($postdata)
		 *
		 * - if GET "$base/sources/", return sources list
		 * - if GET "$base/sources/edit" return sources edit form
		 * - if POST "$base/sources/", call setSources($postdata)
		 */

		if(targetUriPath.equals(EntityManager.getInstance().normalizeURI(prefix + "/create-entity").toString())) {
			if(httpRequest.getMethod() == HttpMethod.GET) {
                //copy into
                HttpResponse response = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
                //response.setContent(ChannelBuffers.wrappedBuffer(htmlContent));

				Channels.write(ctx.getChannel(), Answer.create(new File("data/slse/ui/create_entity_form.html")));
//				Channels.write(ctx.getChannel(),
//						Answer.create(new File("data/slse/ui/create_entity_form.html")));
			}
			else if(httpRequest.getMethod() == HttpMethod.POST) {
                QueryStringDecoder qsd = new QueryStringDecoder("http://blub.blah/?" + httpRequest.getContent().toString(Charset.defaultCharset()));

                String elementsQuery = "";
                String name = "";
                boolean dependsOnSensorValues = false;
                boolean multiNodeQuery = false;

                for(Map.Entry<String, List<String> > entry: qsd.getParameters().entrySet()) {
                    String key = entry.getKey();
                    for(String value: entry.getValue()) {
                        if(entry.getKey().equals("elementsQuery")) {
                                elementsQuery = value;
                        }
                        else if(entry.getKey().equals("name")) {
                                name = value;
                        }
                        else if(key.equals("dependsOnSensorValues") && value.equals("yes")) {
                            dependsOnSensorValues = true;
                        }
                        else if(key.equals("multiNodeQuery") && value.equals("yes")) {
                            multiNodeQuery = true;
                        }
                    }
                }
                
                System.out.println("# adding rule: name=" + name + " dependsOnSensorValues=" + dependsOnSensorValues +
                        " multiNodeQuery=" + multiNodeQuery);

                slseBuilder.addRule(name, elementsQuery, dependsOnSensorValues, multiNodeQuery);

                Channels.write(ctx.getChannel(),
                Answer.create("<html><head><meta http-equiv=\"REFRESH\" content=\"0;url=/\"></head></html>"));

    	    }
        }
        else {
            targetUri = EntityManager.getInstance().toThing(targetUri);
		  Model r = ModelFactory.createDefaultModel();

            if(waitForPolling) {
               System.out.println("# SLSEBackend waiting for esecache: " + targetUri);
                synchronized(eseCache) {
					System.out.println("# SLSEBackend got it");
					while(!eseCache.isPollComplete()) {
                        try {
                            eseCache.wait(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
            }

            System.out.println("# SLSEBackend waiting for slseCache: " + targetUri);
		  synchronized(slseCache) {
			  System.out.println("# SLSEBackend waiting got ite: " + targetUri);
			  if(slseCache.get(targetUri.toString()) == null) {
                  System.out.println("! SLSE not found in cache: " + targetUri.toString() + " returning empty model");
				  System.out.println("What we got is:");
				  for(String s: slseCache.byURI.keySet()) {
					  System.out.println("\"" + s + "\"");
				  }
              }
              else {
			    r.add(slseCache.get(targetUri.toString()).getModel());
              }
		  }
            ChannelFuture future = Channels.write(ctx.getChannel(), r);
            //if(!HttpHeaders.isKeepAlive(httpRequestUri)){
                future.addListener(ChannelFutureListener.CLOSE);
            //}
			System.out.println("# SLSEBackend done: " + targetUri);

     }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
    }

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		// TODO
		super.writeRequested(ctx, e);
	}

	public void pollProxiesForever() {
		eseCache.pollForever();
	}

    public ElementSemanticEntityCache getElementSemanticEntityCache() {
        return eseCache;
    }
}

