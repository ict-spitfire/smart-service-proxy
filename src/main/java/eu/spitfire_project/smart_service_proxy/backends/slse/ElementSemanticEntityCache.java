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

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.tdb.TDBFactory;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Henning Hasemann
 */
public class ElementSemanticEntityCache {

	// proxy -> ESE uris
	private Map<String, Set<String> > knownSEs;
	// uri -> ESE
	private Map<String, ElementSemanticEntity> elementSEs;
	// uri -> timestamp
	private Map<String, Long> validUntil;
	private SLSEBackend backend;
	public Dataset dataset;
	private final String tdbLocation = "data/slse/tdb";
	private Set<String> proxies;
	private long defaultValidInterval = 10 * 1000;
	private Set<ElementSemanticEntityCacheListener> listeners;
	private HashMap<String, Model> oldModels;
	private Set<ElementSemanticEntity> addedEntities;
	private Set<ElementSemanticEntity> changedEntities;
	private boolean pollComplete = false;
	private final boolean pollParallel;
	private int pollTimeoutProxy = 20 * 1000;
	//private final long pollTimeoutEntity = 10 * 1000;

	public ElementSemanticEntityCache(SLSEBackend backend, boolean pollParallel, int pollInterval) {
		elementSEs = new HashMap<String, ElementSemanticEntity>();
		knownSEs = new HashMap<String, Set<String>>();
		this.backend = backend;
		dataset = TDBFactory.createDataset(tdbLocation);
		validUntil = new HashMap<String, Long>();
		proxies = new HashSet<String>();
		this.listeners = new HashSet<ElementSemanticEntityCacheListener>();
		this.pollParallel = pollParallel;
		this.defaultValidInterval = pollInterval;
	}

	void addProxy(String proxy) {
		synchronized(this) { proxies.add(proxy); }
	}
	
	synchronized public ElementSemanticEntity get(String uri) {
		ElementSemanticEntity r = elementSEs.get(uri);
		return r;
	}

	public void pollForever() {
		Thread t = new Thread() {
			@Override
			public void run() {
				if(pollParallel) {
					pollLoopParallel();
				}
				else {
					pollLoop();
				}
			}
		};
		t.start();
	}

	synchronized private void w(long time) {
		try {  wait(time); }
		catch (InterruptedException e) { e.printStackTrace(); }
	}

	private void pollLoopParallel() {
		ExecutorService tpe;
		boolean first = true;
		
		Set<String> proxies_ = new HashSet<String>();
		while(true) {
			if(first) {
				log("first_poll_everything");
			}
			log("poll_everything");
			proxies_.clear();
			synchronized(this) {
				proxies_.addAll(proxies);
				pollComplete = false;
			}
			for(String proxy: proxies_) {
				w(100);
				pollProxy(proxy);
			}

			tpe = Executors.newFixedThreadPool(100);

			oldModels = new HashMap<String, Model>();
			addedEntities = new HashSet<ElementSemanticEntity>();
			changedEntities = new HashSet<ElementSemanticEntity>();
			for(Map.Entry<String, Set<String>> entry: knownSEs.entrySet()) {
				final String proxy = entry.getKey();
				final Set<String> entities = entry.getValue();
				for(final String entity_uri: entities) {
					tpe.execute(new Runnable() {
						@Override
						public void run() {
							pollEntityParallel(entity_uri, proxy);
						}
					});
				} // for entities
			} // for knownSEs

			tpe.shutdown();
			try {
				tpe.awaitTermination(1, TimeUnit.HOURS);
			} catch (InterruptedException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}

			manyEntitiesChanged(changedEntities, oldModels);
			manyEntitiesAdded(addedEntities);

			synchronized(this) {
				pollComplete = true;
				notify();
			}

			log("end_poll_everything");
			if(first) {
				log("end_first_poll_everything");
			}
			first = false;

			w(defaultValidInterval);

		} // while(true)

	}
	
	private void pollLoop() {
		Set<String> proxies_ = new HashSet<String>();
		while(true) {
			log("poll_everything");
			
			proxies_.clear();
			synchronized(this) {
				proxies_.addAll(proxies);
				pollComplete = false;
			}
			for(String proxy: proxies_) {
				w(100);
				pollProxy(proxy);
			}

			oldModels = new HashMap<String, Model>();
			addedEntities = new HashSet<ElementSemanticEntity>();
			changedEntities = new HashSet<ElementSemanticEntity>();
			for(Map.Entry<String, Set<String>> entry: knownSEs.entrySet()) {
				String proxy = entry.getKey();
				for(String entity: entry.getValue()) {
					w(100);
					pollEntity(entity, proxy);
				}
			}
			
			manyEntitiesChanged(changedEntities, oldModels);
			manyEntitiesAdded(addedEntities);
			
			//System.out.println("# polling done");
			synchronized(this) {
				pollComplete = true;
				notify();
			}

			log("end_poll_everything");

			w(defaultValidInterval);
		}
	}

	private synchronized void pollEntity(String entity, String proxy) {
		log("poll_entity " + entity);
		Model m = dataset.getNamedModel(entity);
		try {
		
		if(m != null && !m.isEmpty() && elementSEs.containsKey(entity)) {
			Model old = ModelFactory.createDefaultModel().add(m);
			m.removeAll();
			m.read(entity);
			m.close();
			oldModels.put(entity, old);
			changedEntities.add(get(entity));
			entityChanged(entity, old);
		}
		else {
			if(m != null && !m.isClosed()) { m.close(); }
			m = TDBFactory.createNamedModel(entity, tdbLocation);
			m.read(entity);
			m.close();
			elementSEs.put(entity, new ElementSemanticEntity(this, entity, proxy));
			addedEntities.add(get(entity));
			entityAdded(entity);
		}
		}
		catch(Exception e) {
			System.out.println("# Exception at " + System.currentTimeMillis() + " when polling " + entity);
			e.printStackTrace();
		}
		finally {
			if(m != null && !m.isClosed()) { m.close(); }
		}
		log("end_poll_entity " + entity);
	}

	private void pollEntityParallel(String entity, String proxy) {
		log("poll_entity_parallel " + entity);
		Model m = ModelFactory.createDefaultModel();
		
		synchronized(this) {
			m.add(dataset.getNamedModel(entity));
		}

		if(m != null && !m.isEmpty() && elementSEs.containsKey(entity)) {
			Model old = ModelFactory.createDefaultModel().add(m);
			m.removeAll();
			m.read(entity);
			synchronized(this) {
				Model mdb = dataset.getNamedModel(entity);
				mdb.removeAll();
				mdb.add(m);
				mdb.close();
			//}
			m.close();
			//synchronized(this) {
				oldModels.put(entity, old);
				changedEntities.add(get(entity));
				entityChanged(entity, old);
			}
		}
		else {
			if(m != null) { m.close(); }
			m = ModelFactory.createDefaultModel(); //TDBFactory.createNamedModel(entity, tdbLocation);
			m.read(entity);

			synchronized(this) {
				Model mdb = dataset.getNamedModel(entity);
				mdb.removeAll();
				mdb.add(m);
				mdb.close();
			//}
			m.close();
			//3synchronized(this) {
				elementSEs.put(entity, new ElementSemanticEntity(this, entity, proxy));
				addedEntities.add(get(entity));
				entityAdded(entity);
			}
		}
		log("end_poll_entity_parallel " + entity);
	}

	private void pollProxy(String proxy) {
		log("poll_proxy " + proxy);
		Set<String> newKnown = new HashSet<String>();
		
		URLConnection connection;
		InputStream stream = null;
		BufferedReader reader = null;

		long t_before_polling = System.currentTimeMillis();

		try {
			URL url = new URL(proxy + "/.well-known/servers");
			connection = url.openConnection();
			connection.setReadTimeout(pollTimeoutProxy);
			connection.setRequestProperty("Accept", "application/rdf+xml");
			connection.connect();
			stream = connection.getInputStream();
			InputStreamReader isr = new InputStreamReader(stream);
			reader = new BufferedReader(isr);

			String elementSE;
			while((elementSE = reader.readLine()) != null) {
				if(backend.getEntityManager().getBackend(elementSE) != backend) {
					newKnown.add(elementSE);
				}
			}
		}
		catch(IOException e) {
			System.out.println("# Exception at " + System.currentTimeMillis() + " when polling " + proxy + "/.well-known/servers");
			e.printStackTrace();
		}
		finally {
			if(stream != null) {
				try { stream.close(); } catch(IOException _) { }
			}
			if(reader != null) {
				try { reader.close(); } catch(IOException _) { }
			}
		}
		
		synchronized(this) {
			// TODO: Deletion (keep track of oldKnown for this!)
			knownSEs.put(proxy, newKnown);
		}
		log("end_poll_proxy " + proxy);
	}

	public void registerListener(ElementSemanticEntityCacheListener listener) {
		listeners.add(listener);
	}

	private void entityAdded(String uri) {
		for(ElementSemanticEntityCacheListener l: listeners) {
			l.onElementEntityAdded(uri);
		}
	}
	
	private void manyEntitiesChanged(Set<ElementSemanticEntity> entities, Map<String, Model> oldModels) {
		for(ElementSemanticEntityCacheListener l: listeners) {
			l.onManyElementEntitiesChanged(entities, oldModels);
		}
	}
	
	private void manyEntitiesAdded(Set<ElementSemanticEntity> entities) {
		for(ElementSemanticEntityCacheListener l: listeners) {
			l.onManyElementEntitiesAdded(entities);
		}
	}

	private void entityRemoved(String se, Model old_model) {
	}

	private void entitySetChanged() {
	}

	private void entityChanged(String uri, Model old_model) {
		for(ElementSemanticEntityCacheListener l: listeners) {
			l.onElementEntityChanged(uri, old_model);
		}
	}

	/*
	private String openURL(URL url, long timeout) throws IOException {
		URLConnection connection = url.openConnection();
		connection.setReadTimeout(timeout);
		connection.setRequestProperty("Accept", "application/rdf+xml");
		connection.connect();
		StringWriter writer = new StringWriter();
		IOUtils.copy(connection.getInputStream(), writer, "UTF-8");
		return writer.toString();
	}
	*/

	public synchronized Collection<ElementSemanticEntity> getEntities() {
		return elementSEs.values();
	}
	
	void log(String s) {
		System.out.println(System.currentTimeMillis() + " " + s);
	}

	/**
	 * Only to be called by ServiceLevelSemanticEntity!
	 */
	public Dataset getDataset() {
		return dataset;
	}

	public QueryExecution queryUnion(Query query) {
		return QueryExecutionFactory.create(query, dataset);
	}

	public boolean isPollComplete() {
		return pollComplete;
	}
}

