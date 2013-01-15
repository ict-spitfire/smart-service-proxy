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

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.Lock;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Henning Hasemann
 */
public class ServiceLevelSemanticEntity extends SemanticEntity {
	protected String describes;

	// property -> (element-uri -> value)
	protected Map<String, Map<String, Double>> sensorValues;
	private Map<String, Double> meanValues;
	private Set<String> elements;
	
	protected boolean modelValid;
	private Model model;
    ServiceLevelSemanticEntityCache cache;
	
	ServiceLevelSemanticEntity(ServiceLevelSemanticEntityCache cache, String uri, String describes) {
		this.sensorValues = new HashMap<String, Map<String, Double>>();
		this.describes = describes;
		this.uri = uri;
		this.meanValues = new HashMap<String, Double>();
		this.elements = new HashSet<String>();
		this.model = ModelFactory.createDefaultModel();
		
		// $this owl:sameAs $describes
		this.model.add(
				this.model.createStatement(
					this.model.createResource(uri),
					this.model.createProperty(URIs.sameAs),
					this.model.createResource(describes)
				)
		);
		this.modelValid = false;
        this.cache = cache;
	}

	public boolean containsElementEntity(String uri) {
		for(Map<String, Double> m: sensorValues.values()) {
			if(m.containsKey(uri)) { return true; }
		}
		return false;
	}

	/**
	 * Incorporate sensor values of the given semantic entity into this SLSE.
	 * e must *not* have been added to this entity before!
     * TODO: relies that there is a lock on esecache while calling this
	 * @param e
	 */
	public synchronized void addElementEntity(ElementSemanticEntity e) {
		assert(e != null);
		assert(e.getURI() != null);
		System.out.println("# addESE(" + uri + ") += " + e.getURI());
		Map<String, Double> sv = e.getSensorValues();
		for(Map.Entry<String, Double> entry: sv.entrySet()) {
			String property = entry.getKey();
			Double value = entry.getValue();
			System.out.println("# addESE(" + uri + "): " + property + " = " + value);

			if(!sensorValues.containsKey(property)) { sensorValues.put(property, new HashMap<String, Double>()); }
			double n = sensorValues.get(property).size();
			sensorValues.get(property).put(e.getURI(), value);
			if(!meanValues.containsKey(property)) {
				meanValues.put(property, value);
			}
			else {
				meanValues.put(property, meanValues.get(property) * (n-1)/n + value/n);
			}
		}
		elements.add(e.getURI());
		modelValid = false;
	}

	/**
	 * Remove the sensor values of the semantic entity identified by uri from this SLSE.
	 * @param uri
	 */
	public synchronized void removeElementEntity(String uri) {
		//System.out.println("# removeElementEntity(" + this.uri + ") -= " + uri);
		for(Map.Entry<String, Map<String, Double>> entry: sensorValues.entrySet()) {
			String property = entry.getKey();
			Map<String, Double> values = entry.getValue();
			Double v = values.get(uri);
			if(v != null) {
				values.remove(uri);
				double n = values.size();
				if(n == 0.0) {
					meanValues.remove(property);
				}
				else {
					meanValues.put(property, (meanValues.get(property) - v/(n+1)) * (n+1)/n);
				}
			} // if v
		} // for
		
		for(String property: sensorValues.keySet()) {
			if(sensorValues.get(property).isEmpty()) {
				sensorValues.remove(property);
			}
		}
		elements.remove(uri);
		modelValid = false;
	}

	/**
	 * Update sensor values of the given semantic entity. Only allowed if only sensor values have changed
	 * @param e
	 */
	public synchronized void updateSensorValuesFrom(ElementSemanticEntity e) {
		//System.out.println("# updateESE(" + uri + ") ~= " + e.getURI());
		Map<String, Double> newValues = e.getSensorValues();
		for(Map.Entry<String, Double> entry: newValues.entrySet()) {
			String property = entry.getKey();
			double new_value = newValues.get(property);
			if(!sensorValues.containsKey(property)) {
				sensorValues.put(property, new HashMap<String, Double>());
			}
			if(!sensorValues.get(property).containsKey(uri)) {
				sensorValues.get(property).put(uri, 0.0);
			}
			double old_value = sensorValues.get(property).get(uri);
			//System.out.println("# updateEsE(" + uri + ") " + property + ": " + old_value + " -> " + new_value);
			double n = sensorValues.get(property).size();
			sensorValues.get(property).put(uri, newValues.get(property));
			meanValues.put(property,meanValues.get(property) - old_value / n + new_value / n);
		}
		modelValid = false;
	}

	private synchronized void updateModel() {
		if(modelValid) return;
		
		//System.out.println("Updating SLSE model for " + uri + " with " + elementCount + " element SEs.");
		System.out.println("# updateModel(" + uri + ")");
		for(Map.Entry<String, Double> entry: meanValues.entrySet()) {
			String property = entry.getKey();
			Double value = entry.getValue();
			boolean literal = true;
			String query = String.format(
					"PREFIX ssn: <http://purl.oclc.org/NET/ssnx/ssn#> \n" +
					"PREFIX spit: <http://spitfire-project.eu/ontology/ns/> \n" +
                    "select ?sensor where { " +
                    " <%s> ssn:attachedSystem ?sensor ." +
                    //" ?sensor ssn:observedProperty <%s> . " +
                    " ?sensor spit:obs <%s> . " +
                    "}", uri, property);

			Set<Resource> toDelete = new HashSet<Resource>();
			
			model.enterCriticalSection(Lock.WRITE);
			try {
				QueryExecution qexec = QueryExecutionFactory.create(query, model);
				ResultSet r = qexec.execSelect();
				while(r.hasNext()) {
					QuerySolution s = r.nextSolution();
					toDelete.add(s.getResource("sensor"));
				}
	
				for(Resource s: toDelete) {
					model.remove(model.createResource(uri), model.createProperty(URIs.attachedSystem), s);
					model.removeAll(s, null, null);
				}
                
                for(String element: elements) {
                    model.add(
                       model.createStatement(model.createResource(uri), model.createProperty(URIs.hasPart), model.createResource(element))
                    );
                }
	
				String tmpl = String.format(
                    "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n" +
                    "@prefix dul: <http://www.loa-cnr.it/ontologies/DUL.owl#> .\n" +
					"@prefix spit: <http://spitfire-project.eu/ontology/ns/> .\n" +
                    "@prefix : <%s/static/ontology.owl#> .\n" +
                    "<%s>\n" +
                    "  ssn:attachedSystem [ \n" +
                    "	 spit:obs <%s> ; \n" +
                    "	 spit:value %s \n" +
                    "  ] . \n"
                    , EntityManager.SSP_DNS_NAME,
                    uri,
                    property, literal ? "\"" + value + "\"" : "<" + value + ">"
				);
	
				model.read(new StringReader(tmpl), ".", "N3");
			}
			finally {
				model.leaveCriticalSection();
			}
		}
		modelValid = true;
	}
	
    void log(String s) {
        System.out.println(System.currentTimeMillis() + " " + s);
    }
    
	public Model getModel() {
        log("get_model " + uri+ " " + elements.size());
		updateModel();
        log("end_get_model " + uri);
		return model;
	}

	public boolean isEmpty() {
		return false;
/*
		for(Map.Entry<String, Map<String, Double>> entry: sensorValues.entrySet()) {
			if(!entry.getValue().isEmpty()) { return false; }
		}
		return true;*/
	}

	public String getDescribes() {
		return describes;
	}

}

