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

import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Henning Hasemann
 */
public class ServiceLevelSemanticEntityCache {
	
	private Map<String, ServiceLevelSemanticEntity> byDescribes;
	public Map<String, ServiceLevelSemanticEntity> byURI;
	private SLSEBackend backend;
    private boolean waitForPolling;

    /**
     *
     * @param backend
     * @param waitForPolling if true, wait for the ElementSemanticEntityCache to have finished polling, i.e. until
     *                       data from all nodes is available before answering SLSE requests.
     *                       Note that this can drastically increase response time on the upside of never answering
     *                       with "incomplete" SLSEs.
     */
	public ServiceLevelSemanticEntityCache(SLSEBackend backend, boolean waitForPolling) {
		byURI = new HashMap<String, ServiceLevelSemanticEntity>();
		byDescribes = new HashMap<String, ServiceLevelSemanticEntity>();
		this.backend = backend;
        this.waitForPolling = waitForPolling;
	}

	private String makeURI(String describes) {
		return EntityManager.getInstance().toThing(
				"http://" + EntityManager.SSP_DNS_NAME + ":" + EntityManager.SSP_HTTP_SERVER_PORT +
						backend.getPrefix() +
						URI.create(describes).getHost().replace('[', '_').replace(']', '_') + "/" +
						URI.create(describes).getPath().replace('[', '_').replace(']', '_')
						//+ "/" +
						//URI.create(describes).getFragment()
		).toString();
	}

	/**
	 * Return the cached SLSE.
	 */
	synchronized public ServiceLevelSemanticEntity get(String uri) {
		return byURI.get(uri);
	}
	
	/**
	 * Clear the cache completely.
	 */
	synchronized public void clear() {
		byDescribes.clear();
		byURI.clear();
	}

	public synchronized ServiceLevelSemanticEntity create(String describes) {
		String uri = makeURI(describes);
		ServiceLevelSemanticEntity slse = new ServiceLevelSemanticEntity(this, uri, describes);

        byDescribes.put(slse.getDescribes(), slse);
        byURI.put(slse.getURI(), slse);
        EntityManager.getInstance().entityCreated(URI.create(slse.uri), backend);
        System.out.println("# SLSECache.create " + uri);
		return slse;
	}
	
	/**
	 * Return SLSE object that describes given entity.
	 * Return null if not found.
	 */
	synchronized public ServiceLevelSemanticEntity getDescriberOf(String uri) {
		return byDescribes.get(uri);
	}

	synchronized public void collectGarbage() {
		Set<String> describes = new HashSet<String>(),
				uris = new HashSet<String>();
		
		for(Map.Entry<String, ServiceLevelSemanticEntity> entry: byURI.entrySet()) {
			if(entry.getValue().isEmpty()) {
				uris.add(entry.getKey());
				describes.add(entry.getValue().getDescribes());
			}
		}
		
		for(String uri: uris) {
            System.out.println("# SLSECache collecting garbage: " + uri);
            byURI.remove(uri);
        }
		for(String descr: describes) { byDescribes.remove(descr); }
	}

    public SLSEBackend getBackend() {
        return backend;
    }
}

