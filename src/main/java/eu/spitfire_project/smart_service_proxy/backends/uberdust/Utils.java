package eu.spitfire_project.smart_service_proxy.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 17:23
 * To change this template use File | Settings | File Templates.
 */
public class Utils {
	public static BufferedReader readURL(String uri) {
		URLConnection connection;
		InputStream stream = null;
		BufferedReader reader = null;
		try {
			URL url = new URL(uri);
			connection = url.openConnection();
			connection.setReadTimeout(1000);
		//	connection.setRequestProperty("Accept", "application/rdf+xml");
			connection.connect();
			stream = connection.getInputStream();
			InputStreamReader isr = new InputStreamReader(stream);
			reader = new BufferedReader(isr);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		finally {
		}
		return reader;
	}

	public static void addStatement(Model m, String s, String p, String o) {
		m.add(
				m.createStatement(m.createResource(s), m.createProperty(p), m.createResource(o))
		);
	}
	public static void addValueStatement(Model m, String s, String p, String o) {
		m.add(
				m.createStatement(m.createResource(s), m.createProperty(p), m.createLiteral(o))
		);
	}
}
