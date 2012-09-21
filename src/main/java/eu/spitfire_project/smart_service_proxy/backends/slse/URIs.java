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
	public static final String hasValue = "http://spitfire-project.eu/ontology/ns/value";
}

