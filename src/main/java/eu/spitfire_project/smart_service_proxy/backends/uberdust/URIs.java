package eu.spitfire_project.smart_service_proxy.backends.uberdust;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class URIs {
	public static String base = "";
	static {
		try {
			base = "http://" + InetAddress.getLocalHost().getCanonicalHostName() + ":8080";
		}
		catch(UnknownHostException e) {
		}
	}
	
	public static final String currentValue = base + "/static/ontology.owl#currentValue";
	public static final String slse = base + "/static/ontology.owl#ServiceLevelSemanticEntity";
	
	public static final String attachedSystem = "http://purl.oclc.org/NET/ssnx/ssn#attachedSystem";
	public static final String hasPart = "http://www.loa-cnr.it/ontologies/DUL.owl#hasPart";
	public static final String observes = "http://purl.oclc.org/NET/ssnx/ssn#observes";
	public static final String type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
	public static final String sameAs = "http://www.w3.org/2002/07/owl#sameAs";
	public static final String hasValue = "http://www.loa-cnr.it/ontologies/DUL.owl#hasValue";

	public static final String hasUberdustURI = base + "/static/ontology.owl#hasUberdustURI";
}

