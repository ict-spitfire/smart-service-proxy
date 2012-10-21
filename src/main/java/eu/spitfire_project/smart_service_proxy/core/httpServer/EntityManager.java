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
package eu.spitfire_project.smart_service_proxy.core.httpServer;

import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.UIElement;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

//import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;

/**
 * The EntityManager is the topmost upstream handler of an HTTPEntityMangerPipeline.
 * It contains a list of {@link eu.spitfire_project.smart_service_proxy.core.Backend}s to manage all available services behind them.
 * 
 * @author Henning Hasemann
 * @author Oliver Kleine
 */
public class EntityManager extends SimpleChannelHandler {

    private static Logger log = Logger.getLogger(EntityManager.class.getName());

    private static Configuration config;
    static{
        try {
            config = new PropertiesConfiguration("ssp.properties");
        } catch (ConfigurationException e) {
            log.error("Error while loading config.", e);
        }
    }

    public static final String SSP_DNS_NAME = config.getString("SSP_DNS_NAME", "localhost");
    public static final int SSP_HTTP_SERVER_PORT = config.getInt("SSP_HTTP_SERVER_PORT", 8080);
    public static final String DNS_WILDCARD_POSTFIX = config.getString("IPv4_SERVER_DNS_WILDCARD_POSTFIX", null);


    //Services offered by EntityManager
    private final String PATH_TO_SERVER_LIST = "/.well-known/servers";
    private final String SERVER_PATH_TO_SLSE_UI = "/static/";
    private final String LOCAL_PATH_TO_SLSE_UI = "data/slse/ui/create_entity_form.html";

    //Parameters for Backend and Entity creation
    private int nextBackendId = 0;
    private final String BACKEND_PREFIX_FORMAT = "/be-%04d/";
    private int nextEntityId = 0;
    private final String ENTITY_FORMAT = "/entity-%04x/";



	//Contains the URIs of services (e.g. on sensor nodes) and the proper backend
	private ConcurrentHashMap<URI, Backend> entities = new ConcurrentHashMap<URI, Backend>();

    private ConcurrentHashMap<URI, Backend> virtualEntities = new ConcurrentHashMap<URI, Backend>();

	//Contains the individual paths to the backends (for Userinterface access)
	private ConcurrentHashMap<String, Backend> backends = new ConcurrentHashMap<String, Backend>();

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
		return normalizeURI(String.format(ENTITY_FORMAT, nextEntityId));
	}
	
	/**
	 */
	public void registerBackend(Backend backend) {
		nextBackendId++;
		String prefix = String.format(BACKEND_PREFIX_FORMAT, nextBackendId);
		backend.setPrefix(prefix);
        registerBackend(backend, prefix);
	}
    
    public void registerBackend(Backend backend, String prefix){
        if(backends.put(prefix, backend) != backend){
            log.debug("New Backend for path prefix " + prefix);
        }
        if(backend.getUIElements() != null){
            uiElements.addAll(backend.getUIElements());
        }
    }

    /**
     * Normalizes the given URI. It removes unnecessary slashes (/) or unnecessary parts of the path
     * (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following characters at the
     * end of the path and makes relative URIs absolute.
     *
     * @param uri The URI to be normalized
     */
	public URI normalizeURI(URI uri) {
        return normalizeURI(uri.toString());
    }

    /**
     * Normalizes the given URI. It removes unnecessary slashes (/) or unnecessary parts of the path
     * (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following characters at the
     * end of the path and makes relative URIs absolute.
     *
     * @param uri The URI to be normalized
     */
	public URI normalizeURI(String uri) {
		while(uri.substring(uri.length()-1).equals("#")) {
			uri = uri.substring(0, uri.length()-1);
		}
		URI r = URI.create(SSP_DNS_NAME).resolve(uri).normalize();
		return r;
	}



    /**
     * Normalizes the given URI and adds a # at the end. It removes unnecessary slashes (/) or
     * unnecessary parts of the path (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following
     * characters at the end of the path and makes relative URIs absolute. After normalizing it adds a # at the end.
     * Example: Both, http://localhost/path/to/service#something and http://localhost/path/to/service result into
     * http://localhost/path/to/service#
     *
     * @param uri The URI to be normalized and converted to represent a thing
     */
	public URI toThing(String uri) {
		// note: currently we go for the variant without '#' at the end as
		// that seems to make RDF/XML serializations impossible sometimes
        return URI.create(normalizeURI(uri).toString() /*+ "#"*/);
    }

    /**
     * Normalizes the given URI and adds a # at the end. It removes unnecessary slashes (/) or
     * unnecessary parts of the path (e.g. /part_1/part_2/../path_3 to /path_1/part_3), removes the # and following
     * characters at the end of the path and makes relative URIs absolute. After normalizing it adds a # at the end.
     * Example: Both, http://localhost/path/to/service#something and http://localhost/path/to/service result into
     * http://localhost/path/to/service#
     *
     * @param uri The URI to be normalized and converted to represent a thing
     */
	public URI toThing(URI uri) {
		// note: currently we go for the variant without '#' at the end as
		// that seems to make RDF/XML serializations impossible sometimes
		return URI.create(normalizeURI(uri).toString() /* + "#"*/);
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

        HttpRequest httpRequest = (HttpRequest) e.getMessage();
        URI targetUri = toThing(URI.create("http://" + httpRequest.getHeader("HOST") + httpRequest.getUri()));

        log.debug("Received HTTP request for " + targetUri);

        String targetUriHost = InetAddress.getByName(targetUri.getHost()).getHostAddress();
        //remove leading zeros per block
        targetUriHost = targetUriHost.replaceAll(":0000", ":0");
        targetUriHost = targetUriHost.replaceAll(":000", ":0");
        targetUriHost = targetUriHost.replaceAll(":00", ":0");
        targetUriHost = targetUriHost.replaceAll("(:0)([ABCDEFabcdef123456789])", ":$2");

        //return shortened IP
        targetUriHost = targetUriHost.replaceAll("((?:(?:^|:)0\\b){2,}):?(?!\\S*\\b\\1:0\\b)(\\S*)", "::$2");
        log.debug("Target host: " + targetUriHost);

		String targetUriPath = targetUri.getRawPath();
        log.debug("Target path: " + targetUriPath);

        if(IPAddressUtil.isIPv6LiteralAddress(targetUriHost)){
            targetUriHost = "[" + targetUriHost + "]";
        }

        targetUri = toThing(URI.create("http://" + targetUriHost + httpRequest.getUri()));
        log.debug("Shortened target URI: " + targetUri);

        if(entities.containsKey(targetUri)){
            Backend backend = entities.get(targetUri);
			try {
                ctx.getPipeline().remove("Backend to handle request");
			}
            catch(NoSuchElementException ex) {
                //Fine. There was no handler to be removed.
            }
            ctx.getPipeline().addLast("Backend to handle request", backend);
            log.debug("Forward request to " + backend);
        }

        else if(virtualEntities.containsKey(targetUri)){
            Backend backend = virtualEntities.get(targetUri);
			try {
                ctx.getPipeline().remove("Backend to handle request");
			}
            catch(NoSuchElementException ex) {
                //Fine. There was no handler to be removed.
            }
			ctx.getPipeline().addLast("Backend to handle request", backend);
            log.debug("Forward request to " + backend);
        }

//        else if (targetUriPath.equals(PATH_TO_SERVER_LIST)) {
//            // Handle request for resource at path ".well-known/core"
//            StringBuilder buf = new StringBuilder();
//            for(URI entity: getServices()) {
//                buf.append(toThing(entity).toString() + "\n");
//            }
//            Channels.write(ctx.getChannel(), Answer.create(buf.toString()).setMime("text/plain"));
//            return;
//        }

//        else if("/visualizer".equals(targetUriPath)){
//            try {
//                ctx.getPipeline().remove("Backend to handle request");
//            }
//            catch(NoSuchElementException ex) {
//                //Fine. There was no handler to be removed.
//            }
//            ctx.getPipeline().addLast("VisualizerService", VisualizerService.getInstance());
//            log.debug("Forward request to visualizer.");
//        }

		/*else if(targetUriPath.startsWith(SERVER_PATH_TO_SLSE_UI)) {
			String f = LOCAL_PATH_TO_SLSE_UI + targetUriPath.substring(SERVER_PATH_TO_SLSE_UI.length());
			Channels.write(ctx.getChannel(), Answer.create(new File(f)).setMime("text/n3"));
		}*/

		else if("/".equals(targetUriPath)){
            HttpResponse httpResponse =
                    new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);

            httpResponse.setContent(getHtmlListOfServices());
            ChannelFuture future = Channels.write(ctx.getChannel(), httpResponse);
            future.addListener(ChannelFutureListener.CLOSE);
            return;
        }

        ctx.sendUpstream(e);
    }

    private ChannelBuffer getHtmlListOfServices(){
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
        for(Map.Entry<URI, Backend> entry: entities.entrySet()) {
            buf.append(String.format("<li><a href=\"%s\">%s</a></li>\n", entry.getKey(), entry.getKey()));
        }
        buf.append("</ul>\n");

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString()
                                               .getBytes(Charset.forName("UTF-8")));
    }

	/**
	 * Return URIs for all known services.
	 */
	public Iterable<URI> getServices(){
		return entities.keySet();
	}
	
	/**
	 * Will be called when an entity has been created.
	 * uri may be null in which case the return value will be a newly
	 * allocated URI for the entity.
	 */
	public URI entityCreated(URI uri, Backend backend) {
		if(uri == null) {
			uri = nextEntityURI();
		}
		uri = toThing(uri);
		entities.put(uri, backend);
		log.debug("New entity created: " + uri);
		
		return uri;
	}

    public URI virtualEntityCreated(URI uri, Backend backend) {
        uri = toThing(uri);
        virtualEntities.put(uri, backend);
        log.debug("New virtual entity created: " + uri);
        return uri;
    }
	
	public Backend getBackend(String elementSE) {
		Backend b = entities.get(elementSE);
		if(b == null) {
			URI uri = URI.create(SSP_DNS_NAME).resolve(elementSE).normalize();
			String path = uri.getRawPath();
			
			String pathPart = path.substring(0, path.indexOf("/"));
			b = backends.get(pathPart);
		}
		return b;
	}
}


