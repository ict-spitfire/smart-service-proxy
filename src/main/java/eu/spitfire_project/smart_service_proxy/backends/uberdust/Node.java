package eu.spitfire_project.smart_service_proxy.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 17:18
 * To change this template use File | Settings | File Templates.
 */
public class Node {
	private String uberdustURI;
	private String entityURI;

	public Node(String entityURI, String uberdustURI) {
		this.uberdustURI = uberdustURI;
		this.entityURI = entityURI;
	}
	
	public Vector<String> getCapabilities() {
		Vector<String> r = new Vector<String>();
		String uri = uberdustURI + "/capabilities";
		BufferedReader reader = Utils.readURL(uri);
		String line = null;
		try {
			while((line = reader.readLine()) != null) {
				r.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
		return r;
	}
	
	public Observation getLatestObservation(String capability) {
		String uri = uberdustURI + "/capability/" + capability + "/latestreading";
		BufferedReader reader = Utils.readURL(uri);
		String obs = null;
		try {
			obs = reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			return null;
		}
		if(obs == null) { return null; }
		return Observation.fromLine(obs);
	}
	
	public String[] getPosition() {
		Pattern p = Pattern.compile("<georss:point>(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)</georss:point>");
		BufferedReader reader = Utils.readURL(uberdustURI + "/georss");
		if(reader == null) { return null; }

		String line = null;
		try {
			while((line = reader.readLine()) != null) {
				Matcher m = p.matcher(line);
				if(m.matches()) {
					String[] r = new String[2];
					r[0] = m.group(0);
					r[1] = m.group(1);
					return r;
				}
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Model getDescription() {
		Model m = ModelFactory.createDefaultModel();
		Resource me = m.createResource(entityURI);
		Utils.addStatement(m, entityURI, URIs.hasUberdustURI, uberdustURI);
		
		String turtleTemplate =
				"@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n" +
				"@prefix dul: <http://www.loa-cnr.it/ontologies/DUL.owl#> .\n" +
				"@prefix dc: <http://purl.org/dc/terms/> . \n" +
			    "@prefix spitfire: <http://spitfire-project.eu/cc/spitfireCC_n3.owl#> . \n" +
				"\n" +
				"<%s> ssn:attachedSystem [ \n" +
				"  a ssn:Sensor ; \n" +
				"  ssn:observedProperty <%s> ; \n" +
				"  dul:hasValue \"%s\" ; \n" +
				"  dc:date \"%s\" ; \n" +
			    "  spitfire:uomInUse <%s> \n" +
				"] . \n";
		
		for(String cap: getCapabilities()) {
			try {
				Observation observation = getLatestObservation(cap);
				Capability capability = CapabilityMap.getInstance().getByURN(cap);
				String turtle = String.format(turtleTemplate,
						entityURI,
						capability.getObservedProperty(),
						observation.getValue(),
						observation.getTimestamp(),
						capability.getUomInUse()
				);
				
				m.read(new StringReader(turtle), entityURI, "N3");
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		String[] position = getPosition();
		if(position != null) {
			Utils.addValueStatement(m, entityURI, "spitfire:hasPositionTODO", position[0] + " " + position[1]);
		}
		
		return m;
	}

}
