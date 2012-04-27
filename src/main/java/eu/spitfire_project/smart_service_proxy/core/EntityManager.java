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
package eu.spitfire_project.smart_service_proxy.core;

import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;

//import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;

/**
 * The EntityManager is the topmost upstream handler of an HTTPEntityMangerPipeline. It contains a list of {@link Backend}s to manage
 * all available entities behind them.
 * 
 * @author Henning Hasemann
 * @author Oliver Kleine
 */
public class EntityManager extends SimpleChannelHandler {

    private static Logger log = Logger.getLogger(EntityManager.class.getName());

	private int backendId = 0;
	//Contains the URIs of services (e.g. on sensor nodes) and the proper backend
	private ConcurrentHashMap<URI, Backend> entityBackends = new ConcurrentHashMap<URI, Backend>();
	//Contains the individual paths to the backends (for Userinterface access)
	private ConcurrentHashMap<String, Backend> pathBackends = new ConcurrentHashMap<String, Backend>();

	private final String listPath = "/.well-known/servers";
	private String uriBase;
	private final String backendPrefixFormat = "/be-%04d/";
	private final String entityURIFormat = "/entity-%04x/";
	private final String staticPrefix = "/static/";
	private final String staticDirectory = "data/static/";
	private final int backendPrefixLength = String.format(backendPrefixFormat, 0).length();
	
	private int nextEntityId = 0;
	private Vector<UIElement> uiElements = new Vector<UIElement>();

    //Make EntityManager a Singleton
	private static EntityManager instance = new EntityManager();
	
	private EntityManager(){
	}
	
	public static EntityManager getInstance() {
        return instance;
    }
	
	/**
	 */
	URI nextEntityURI() {
		nextEntityId++;
		return normalizeURI(String.format(entityURIFormat, nextEntityId));
	}
	
	/**
	 */
	public void registerBackend(Backend backend) {
		backendId++;
		String prefix = String.format(backendPrefixFormat, backendId);
		backend.setPathPrefix(prefix);
        registerBackend(backend, prefix);
	}
    
    public void registerBackend(Backend backend, String prefix){
        if(pathBackends.put(prefix, backend) != backend){
            log.debug("New Backend for path prefix " + prefix);
        };
        if(backend.getUIElements() != null){
            uiElements.addAll(backend.getUIElements());
        }
    }

	
	/**
	 * Normalize uri.
	 * I.e.
	 * - Normalize syntactically (remove unnecessary /'s etc....)
	 * - Normalize semantically (if relative uri, make absolute)
	 * - Strip "#something"-part
	 */
	public URI normalizeURI(URI uri) { return normalizeURI(uri.toString()); }
	
	/// ditto
	public URI normalizeURI(String uri) {
		while(uri.substring(uri.length()-1).equals("#")) {
			uri = uri.substring(0, uri.length()-1);
		}
		URI r = URI.create(uriBase).resolve(uri).normalize();
		return r;
	}
	
	/// ditto
	public URI toDocument(String uri) { return normalizeURI(uri); }
	/// ditto
	public URI toDocument(URI uri) { return normalizeURI(uri); }
	
	/**
	 * Like \ref toDocument but add "#" to the URI so as to refer to a
	 * semantic object instead of the document describing it.
	 * Do not use this on already correct semantic URIs, as it will strip the
	 * "#something" part and replace it with just "#"!
	 */
	public URI toThing(String uri) { return URI.create(normalizeURI(uri).toString() + "#"); }
	
	public URI toThing(URI uri) { return URI.create(normalizeURI(uri).toString() + "#"); }
	
	/**
	 */
	public String getURIBase() {
		return uriBase;
	}
	
	public void setURIBase(String uriBase) {
		this.uriBase = uriBase;
	}
	
	/**
	 * Expected Message types:
	 * - HTTP Requests
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        if(!(e.getMessage() instanceof HttpRequest)) {
			super.messageReceived(ctx, e);
            return;
		}

        HttpRequest request = (HttpRequest) e.getMessage();

        if(log.isDebugEnabled()){
            log.debug("[EntityManager] Received HTTP request for target: " + request.getUri());
        }
		
        
		URI uri = URI.create(uriBase).resolve(request.getUri()).normalize();
		String path = uri.getRawPath();

        String hostHeader = request.getHeader(HOST);
        System.out.println("Host Header:" + hostHeader);
        
        System.out.println("Anzahl Backends: " + pathBackends.values().size());
        
        for(Backend backend : pathBackends.values()){
            System.out.println("Class of Backend: " + backend.getClass());
            if(backend instanceof CoapBackend){
                CoapBackend coapBackend = (CoapBackend) backend;
                System.out.println("CoapBackend Prefix: " + coapBackend.getIpv6Prefix());
                System.out.println("HttpRequest Host Header: " + hostHeader);
                if(hostHeader.indexOf(coapBackend.getIpv6Prefix()) != -1){
                    ctx.getPipeline().addLast("Backend to handle request", coapBackend);
                    System.out.println("EntityManager: Forward Request to CoapBackend!!!");
                    ctx.sendUpstream(e);
                    return;
                }
            }
        }
            
        
        if(path.equals(listPath)) {
            // Handle request for resource at path ".well-known/core"
			StringBuilder buf = new StringBuilder();
			for(URI entity: getEntities()) {
				buf.append(toThing(entity).toString() + "\n");
			}
			Channels.write(ctx.getChannel(), Answer.create(buf.toString()).setMime("text/plain"));
		}
        else if(path.startsWith(staticPrefix)) {
            String f = staticDirectory + path.substring(staticPrefix.length());
            Channels.write(ctx.getChannel(), Answer.create(new File(f)).setMime("text/n3"));
        }

        else if(path.length() >= backendPrefixLength) {

            String prefix = path.substring(0, path.indexOf("/", 1) + 1);

            //Create /64-Prefix for IPv6
            if(prefix.startsWith("/%5B")){
                prefix = prefix.substring(4, prefix.length() - 1);
                String[] components = prefix.split(":");
                prefix = "/%5B";
                for(int i = 0; i < 4; i++){
                    prefix += (components[i] + ":");
                }
                //Remove the last ":"
                prefix = prefix.substring(0, prefix.length() - 1);
            }

            log.debug("Try to find backend for prefix " + prefix);
            
            //Find backend for prefix
            if(pathBackends.containsKey(prefix)){
                // Get resource from appropriate backend and send response
                Backend be = pathBackends.get(prefix);  //entityBackends.get(toThing(uri));
                try{
                    ctx.getPipeline().remove("Backend to handle request");
                }catch(NoSuchElementException ex){
                    //No such backend in the pipeline and thus nothing to remove. That's fine.
                }
                ctx.getPipeline().addLast("Backend to handle request", be);
                ctx.sendUpstream(e);

		    }
            else {
                log.debug("! No backend found to handle path " + path + "  prefix=" + prefix);
            }
		}
//		// Handle request for UserInterface access
//		else if((pathBackendPrefix != null) && pathBackends.containsKey(pathBackendPrefix)) {
//			pathBackends.get(pathBackendPrefix).handleUpstream(ctx, e);
//		}
		else {
			StringBuilder buf = new StringBuilder();
			buf.append("<html><body>\n");
			buf.append("<h1>Smart Service Proxy</h1>\n");
			buf.append("<h2>Operations</h2>\n");
			buf.append("<ul>\n");
			for(UIElement elem: uiElements) {
				buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", elem.getURI(), elem.getTitle()));
			}
			buf.append("</ul>\n");
			
			buf.append("<h2>Entities</h2>\n");
			buf.append("<ul>\n");
			for(Map.Entry<URI, Backend> entry: entityBackends.entrySet()) {
				buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", entry.getKey(), entry.getKey()));
			}
            buf.append("</ul>\n");

            //Retreive resources from all registered Backends
            for(Backend backend : pathBackends.values()){
                Set<URI> resourceURIs = backend.getResources();
                if(!resourceURIs.isEmpty()){
                    buf.append("<h3> " + backend.getClass().getSimpleName() + "</h3>\n");
                    buf.append("<ul>\n");

                    for(URI resourceURI : resourceURIs){
                        buf.append("<li><a href=\"" + resourceURI.toString() + "\">" +
                                resourceURI.toString() + "</a></li>\n");
                    }
                
                    buf.append("</ul>\n");
                }
            }
            
			
			
			buf.append("</body></html>\n");
			Channels.write(ctx.getChannel(), Answer.create(buf.toString()));
		}
	} // handleHttpRequest
	
	/**
	 * Outbound Message types:
	 * - SelfDescription
	 */
	/*
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		//TODO write the requested SelfDescription to the Downstream
		System.out.println("Nachricht soll gesendet werden");
		super.handleDownstream(ctx, e);
	}
	*/
	
	
	/**
	 * Return URIs for all known entities.
	 */
	private Iterable<URI> getEntities() {
		return entityBackends.keySet();
	}
	
	/**
	 * Will be called when an entity has been created.
	 * uri may be null in which case the return value will be a newly
	 * allocated URI for the entity.
	 */
	public URI entityCreated(URI uri, Backend backend) {
        log.debug("Create new entity: " + uri);
		if(uri == null) {
			uri = nextEntityURI();
		}
		uri = toThing(uri);
		entityBackends.put(uri, backend);
		//System.out.println("[EntityManager] New entity created: " + uri);
		
		return uri;
	}
	
	/**
	 * Will be called by a bounded Backend when an entity has been destroyed.
	 */
	/*public void entityDestroyed(URI uri) {
		entityBackends.remove(uri);
	}*/
	
	/**
	 * Will be called by a bounded Backend whenever the description of an entity changed.
	 */
	/*public void descriptionChanged(URI entity, Model model) {
		// TODO
	}*/

	public Backend getBackend(String elementSE) {
		Backend b = entityBackends.get(elementSE);
		if(b == null && elementSE.length() >= backendPrefixLength) {
			URI uri = URI.create(uriBase).resolve(elementSE).normalize();
			String path = uri.getRawPath();
			
			if(path.length() < backendPrefixLength) { return null; }

			String pathPart = path.substring(0, backendPrefixLength);
			b = pathBackends.get(pathPart);
		}
		return b;
	}
}


