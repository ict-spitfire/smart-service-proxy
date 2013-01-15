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

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.shared.Lock;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Henning Hasemann
 */
public abstract class SemanticEntity {
	protected long expires;
	//protected Model model;
	protected String uri;
	
	protected static Query sensorQuery = QueryFactory.create(
			"select ?property ?value where {" +
					//" ?sensor a <http://purl.oclc.org/NET/ssnx/ssn#Sensor> . " +
					//" ?sensor <http://www.loa-cnr.it/ontologies/DUL.owl#hasValue> ?value . " +
					" ?sensor <http://spitfire-project.eu/ontology/ns/value> ?value . " +
					//" ?sensor <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?property . " +
					" ?sensor <http://spitfire-project.eu/ontology/ns/obs> ?property . " +
					"}"
	);

	SemanticEntity() {
		//model = ModelFactory.createDefaultModel();
	}
	
	public abstract Model getModel();

	public String getURI() {
		return uri;
	}

	public Map<String,Double> getSensorValues() {
		Map<String, Double> r = new HashMap<String, Double>();

        Model model = getModel();
			
		model.enterCriticalSection(Lock.READ);
		try {
			QueryExecution qexec = QueryExecutionFactory.create(sensorQuery, model);
			try {
				ResultSet results = qexec.execSelect();
				while(results.hasNext()) {
					QuerySolution solution = results.nextSolution();
					String property = solution.getResource("property").getURI();
					RDFNode value = solution.get("value");

					// If value is a literal, compute sum and count of all
					// values
					if(value.isLiteral()) {
						System.out.println("--------- PITTING SENSOR VALUE " + property + " = " + value.asLiteral().toString());
						r.put(property, value.asLiteral().getDouble());
					}

					// If value is something else, just keep the value of the
					// sensor node with the "minimum" URI
					else if(value.isURIResource()) {
						System.out.println("--------- SENSOR VALUE IS URI!!!!");
						// TODO
					}
				} // while results

			}
			finally { qexec.close(); }
		}
		finally {
			model.leaveCriticalSection();
		}

			model.close();
		return r;
	}
}

