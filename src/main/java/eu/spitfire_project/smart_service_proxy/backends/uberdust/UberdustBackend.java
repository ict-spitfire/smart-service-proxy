package eu.spitfire_project.smart_service_proxy.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 15:49
 * To change this template use File | Settings | File Templates.
 */
public class UberdustBackend extends Backend {
	// uberdust-server --> vector of testbed-ids
	Map<String, Vector<String>> uberdustTestbeds;

	public UberdustBackend() {
		uberdustTestbeds = new HashMap<String, Vector<String>>();

		CapabilityMap capabilityMap;
		capabilityMap = CapabilityMap.getInstance();
		capabilityMap.add("urn:wisebed:node:capability:temperature",
			new Capability("http://dbpedia.org/resource/Temperature", "http://dbpedia.org/resource/Centigrade"));
		capabilityMap.add("urn:wisebed:node:capability:light",
			new Capability("http://dbpedia.org/resource/Light", "http://dbpedia.org/resource/Lumen"));
	}
	
	public void addUberdustTestbed(String server, String testbed) {
		if(!uberdustTestbeds.containsKey(server)) {
			uberdustTestbeds.put(server, new Vector<String>());
		}
		uberdustTestbeds.get(server).add(testbed);
	}

	@Override
	public void bind() {
		super.bind();
		for(Map.Entry<String, Vector<String>> entry: uberdustTestbeds.entrySet()) {
			for(String testbed: entry.getValue()) {
				exploreUberdustTestbed(entry.getKey(), testbed);
			}
		}
	}

	void exploreUberdustTestbed(String server, String testbed) {
		String nodeList = server + "/uberdust/rest/testbed/" + testbed + "/node";
		
		URLConnection connection;
		InputStream stream = null;
		BufferedReader reader = null;
		try {
			URL url = new URL(nodeList);
			System.out.println("# Polling: " + nodeList);
			connection = url.openConnection();
			connection.setReadTimeout(1000);
			connection.setRequestProperty("Accept", "application/rdf+xml");
			connection.connect();
			stream = connection.getInputStream();
			InputStreamReader isr = new InputStreamReader(stream);
			reader = new BufferedReader(isr);

			String urn;
			while((urn = reader.readLine()) != null) {
				EntityManager.getInstance().entityCreated(URI.create(
						makeEntityURI(
								URI.create(server).getHost() + ":" + URI.create(server).getPort(),
								testbed, urn
						)
				), this);
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		finally {
			if(stream != null) {
				try { stream.close(); } catch(IOException _) { }
			}
			if(reader == null) {
				try { reader.close(); } catch(IOException _) { }
			}
		}
		
	}

	String makeUberdustURI(String server, String testbed, String urn) {
		return "http://" + server + "/uberdust/rest/testbed/" + testbed + "/node/" + urn;
	}
	
	String makeEntityURI(String server, String testbed, String urn) {
		return EntityManager.SSP_DNS_NAME + getPrefix() + "/" + server + "/" + testbed + "/" + urn;
	}
	
	String entityPathtoUberdustURI(String path) {
		String[] pathParts = path.split("/");
		String server = pathParts[0];
		String testbed = pathParts[1];
		String urn = pathParts[2];
		return makeUberdustURI(server, testbed, urn);
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		if(!(e.getMessage() instanceof HttpRequest)){
			super.messageReceived(ctx, e);
		}
		HttpRequest request = (HttpRequest) e.getMessage();
		URI uri = URI.create(request.getUri());
		URI entityURI = URI.create(EntityManager.SSP_DNS_NAME).resolve(uri);
		String path = uri.getPath();
		String postfix = path.substring(getPrefix().length());
		if(postfix == null) {
			super.messageReceived(ctx, e);
			return;
		}
		
		System.out.println(postfix + " -> " + entityPathtoUberdustURI(postfix));
		Node node = new Node(entityURI.toString(), entityPathtoUberdustURI(postfix));

		Model m = node.getDescription();

		ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, uri));
		if(!HttpHeaders.isKeepAlive(request)){
			future.addListener(ChannelFutureListener.CLOSE);
		}
		
	}
}
