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
package eu.spitfire_project.smart_service_proxy.core.wiselib_interface;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 25.01.12
 * Time: 12:56
 * To change this template use File | Settings | File Templates.
 */
public class SimpleQueryStatement {
	private String subject, predicate, object;
	private boolean subjectVariable,
		predicateVariable,
		objectVariable,
		objectLiteral;
	
	SimpleQueryStatement(String s, String p, String o, boolean objectLiteral) {
		subjectVariable = s.substring(0,1).equals("?");
		predicateVariable = p.substring(0,1).equals("?");
		if(!objectLiteral) {
			objectVariable = o.substring(0,1).equals("?");
		}
		else {
			objectVariable = false;
		}
		this.objectLiteral = objectLiteral;

		this.subject = s.substring(subjectVariable ? 1 : 0);
		this.predicate = p.substring(predicateVariable ? 1 : 0);
		this.object = o.substring(objectVariable ? 1 : 0);
	}
	
	String getSubject() { return subject; }
	boolean isSubjectVariable() { return subjectVariable; }
	String getPredicate() { return predicate; }
	boolean isPredicateVariable() { return predicateVariable; }
	String getObject() { return object; }
	boolean isObjectVariable() { return objectVariable; }
	boolean isObjectLiteral() { return objectLiteral; }

}
