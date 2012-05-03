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
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.tdb.TDB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServiceLevelSemanticEntityBuilder implements ElementSemanticEntityCacheListener {
	private final ElementSemanticEntityCache eseCache;
	private final ServiceLevelSemanticEntityCache slseCache;
	
    private class ConstructionRule {
        public Query query;
        public String name;
        public boolean dependsOnSensorValues;
        public boolean multiNodeQuery;

        public ConstructionRule(String name, Query query, boolean dependsOnSensorValues, boolean multiNodeQuery) {
            this.name = name;
            this.query = query;
            this.dependsOnSensorValues = dependsOnSensorValues;
            this.multiNodeQuery = multiNodeQuery;
        }

        public boolean dependsOnSensorValues() {
            return dependsOnSensorValues;
        }

        public Query getQuery() {
            return query;
        }
    }
    
	// name or id --> query
	private Map<String, ConstructionRule> constructionRules;
	private SLSEBackend backend;

	// element-uri -> set of SLSE uris the element is part of
	private Map<String, Set<String>> slseByElement;

	ServiceLevelSemanticEntityBuilder(ElementSemanticEntityCache esec, ServiceLevelSemanticEntityCache slsec, SLSEBackend backend) {
		this.eseCache = esec;
		this.slseCache = slsec;
		this.constructionRules = new HashMap<String, ConstructionRule>();
		this.backend = backend;
		this.slseByElement = new HashMap<String, Set<String>>();

		this.eseCache.registerListener(this);
	}

	/*
	 */
	private void removeSensorValues(String uri, Model model) {
		// TODO: Make sure, subject is a sensor (i.e. there is a (uri, attachedSystem, X)
		//synchronized(ElementSemanticEntityCache.getEntityLock(uri)) {
		model.enterCriticalSection(Lock.WRITE);
		try {
			SimpleSelector selector = new SimpleSelector(null, model.createProperty(URIs.hasValue), (Object)null);
			model.remove(model.listStatements(selector).toList());
		}
		finally {
			model.leaveCriticalSection();
		}
	}
    
    void log(String s) {
        System.out.println(System.currentTimeMillis() + " " + s);
    }

	/**
	 * Return all SLSEs the given SE belongs to.
	 * Creates all not-yet existing returned SLSEs in the slse cache.
	 */
    private Map<String, Set<String>> getAndCreateSLSEs(QueryExecution qexec) { //String uri, ConstructionRule rule) {
        Map<String, Set<String>> r = new HashMap<String, Set<String>>();
        /*List<Map<String, RDFNode>> queryResult =*/
        ResultSet results = qexec.execSelect(); //eseCache.queryEntity(uri, rule.query);
        
        while(results.hasNext()) {
            QuerySolution solution = results.nextSolution();
            if(solution.getResource("element") == null) { continue; }
            if(solution.getResource("entity") == null) { continue; }
            if(solution.getResource("element").getURI() == null) { continue; }
            if(solution.getResource("entity").getURI() == null) { continue; }
            
            String element = solution.getResource("element").getURI();
            String describes = solution.getResource("entity").getURI();
            
            ServiceLevelSemanticEntity slse = slseCache.getDescriberOf(describes);
            if(slse == null) {
                slse = slseCache.create(describes);
            }
            
            if(!r.containsKey(element)) {
                r.put(element, new HashSet<String>());
            }
            r.get(element).add(slse.getURI());
        }
         //slseByElement.put(uri, r);

        return r;
    }

    private boolean changedOnlyInSensorValue(String uri, Model new_model, Model old_model) {
        //Model new_model = eseCache.get(uri).getModelCopy();
        removeSensorValues(uri, Utils.copyModel(new_model));

        Model old_model_copy = Utils.copyModel(old_model);
        removeSensorValues(uri, old_model_copy);
        return new_model.isIsomorphicWith(old_model_copy);
    }

	/**
	 */
	@Override
	public synchronized void onElementEntityChanged(String uri, Model old_model) {
        synchronized(eseCache) {
            boolean onlySensorValueChanged = changedOnlyInSensorValue(uri, eseCache.get(uri).getModel(), old_model);

            Model new_model = eseCache.get(uri).getModel();

            for(ConstructionRule rule: constructionRules.values()) {
                if(onlySensorValueChanged && !rule.dependsOnSensorValues()) {
                    for(String slse: slseByElement.get(uri)) {
                        slseCache.get(slse).updateSensorValuesFrom(eseCache.get(uri));
                    }
                }
                else {
                    // SLSEs that include the given element semantic entity
                    Set<String> new_slses = new HashSet<String>();
                    Set<String> r = getAndCreateSLSEs(QueryExecutionFactory.create(rule.getQuery(), new_model)).get(uri);
                    if(r != null) {
                        new_slses.addAll(r);
                    }

                    // SLSEs we need to remove the ESE from
                    Set<String> remove_from = new HashSet<String>();
					if(!slseByElement.get(uri).isEmpty())
						remove_from.addAll(slseByElement.get(uri));
                    remove_from.removeAll(new_slses);

                    // SLSEs we need to add the ESE to
                    Set<String> add_to = new HashSet<String>();
                    add_to.addAll(new_slses);
                    add_to.removeAll(slseByElement.get(uri));

                    for(String s: remove_from) {
                        if(slseCache.get(s) != null) {
                            slseCache.get(s).removeElementEntity(uri);
                        }
                    }

                    for(String s: add_to) {
                        if(slseCache.get(s) != null) {
                            slseCache.get(s).addElementEntity(eseCache.get(uri));
                        }
                    }

                    slseByElement.put(uri, new_slses);
                    slseCache.collectGarbage();
                }
            }

        } // sync(eseCache)
	}
    
    /**
     * @param uri
     */
    @Override
	public synchronized void onElementEntityAdded(String uri) {
        Model new_model = eseCache.get(uri).getModel();

		// SLSEs that include the given element semantic entity
		Set<String> new_slses = new HashSet<String>();
        //System.out.println("# applying construction rules to " + uri);
        //System.out.println("# new_model: " + new_model);
		for(ConstructionRule rule: constructionRules.values()) {
            Set<String> r = getAndCreateSLSEs(QueryExecutionFactory.create(rule.getQuery(), new_model)).get(uri);
            if(r != null) {
                new_slses.addAll(r);
            }
		}
        //System.out.println("# adding " + uri + " to SLSEs");

		for(String s: new_slses) {
			slseCache.get(s).addElementEntity(eseCache.get(uri));
		}
		slseByElement.put(uri, new_slses);
	}

    /**
     * 
     */
    @Override
    public synchronized void onManyElementEntitiesAdded(Set<ElementSemanticEntity> uris) {
        
    }

    /**
     * 
     */
    @Override
    public synchronized void onManyElementEntitiesChanged(Set<ElementSemanticEntity> entities, Map<String, Model> oldModels) {
        // for all multi-node queries
            // check if query is dependant on sensor values
            // if no and all uris have only changed in value
                // update slses
            // else
                // run queries on whole dataset
                // updateSLSEsForElement(String elementUri, query result)
        synchronized(eseCache) {

        boolean allChangedOnlyInValues = true;
        for(ElementSemanticEntity entity: entities) {
            if(!changedOnlyInSensorValue(entity.getURI(), entity.getModel(), oldModels.get(entity.getURI()))) {
                allChangedOnlyInValues = false;
                break;
            }
        }
        
        for(ConstructionRule rule: constructionRules.values()) {
            if(allChangedOnlyInValues && rule.dependsOnSensorValues()) {
                for(ElementSemanticEntity element: entities) {
                    for(String slseUri : slseByElement.get(element.getURI())) {
                        slseCache.get(slseUri).updateSensorValuesFrom(element);
                    }
                }
            }
            else {
                Map<String, Set<String>> new_associations = getAndCreateSLSEs(eseCache.queryUnion(rule.getQuery()));
                updateElementSLSEAssociations(new_associations);
            }
        }

        }
    }

    private void updateElementSLSEAssociations(Map<String, Set<String>> new_associations) {
        for(Map.Entry<String, Set<String>> entry: new_associations.entrySet()) {
            String element = entry.getKey();
            Set<String> new_slses = entry.getValue();
            Set<String> current_sles = slseByElement.get(element);
            
            // SLSEs we need to remove the ESE from
            Set<String> remove_from = new HashSet<String>();
            remove_from.addAll(current_sles);
            remove_from.removeAll(new_slses);

            // SLSEs we need to add the ESE to
            Set<String> add_to = new HashSet<String>();
            add_to.addAll(new_slses);
            add_to.removeAll(current_sles);

            for(String s: remove_from) {
                slseCache.get(s).removeElementEntity(element);
            }

            for(String s: add_to) {
                slseCache.get(s).addElementEntity(eseCache.get(element));
            }

            slseByElement.put(element, new_slses);
        }
        slseCache.collectGarbage();
    }

    /**
     *
     * @param name
     * @param elementsQuery
     * @param dependsOnSensorValues
     * @param multiNodeQuery
     */
	public void addRule(String name, String elementsQuery, boolean dependsOnSensorValues, boolean multiNodeQuery) {

        synchronized(eseCache) {

        // wait until eseCache is done polling
        /*
        while(!eseCache.isPollComplete()) {
            try {
                eseCache.wait(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        */
        
        
            log("add_rule " + name);

    		Set<ElementSemanticEntity> entities = new HashSet<ElementSemanticEntity>();
            entities.addAll(eseCache.getEntities());

	    	Query query = QueryFactory.create(elementsQuery);
            ConstructionRule rule = new ConstructionRule(name, query, dependsOnSensorValues, multiNodeQuery);
			constructionRules.put(name, rule);

            if(multiNodeQuery) {
                //System.out.println("# multi-node query");
                QueryExecution qexec = eseCache.queryUnion(query);
                qexec.getContext().set(TDB.symUnionDefaultGraph, true);
                Map<String, Set<String>> slses = getAndCreateSLSEs(qexec);
                for(Map.Entry<String, Set<String>> entry: slses.entrySet()) {
                    String element = entry.getKey();
                    Set<String> new_slses = entry.getValue();
                    for(String slse: new_slses) {
                        if(!slseCache.get(slse).containsElementEntity(element)) {
                            slseCache.get(slse).addElementEntity(eseCache.get(element));
                        }
                    }
                }
            }
            else {
                //System.out.println("# single-node query");
                // For all existing element entities
                for(ElementSemanticEntity entity: entities) {
                    // execute the query on it
                    Map<String, Set<String>> slses = getAndCreateSLSEs(QueryExecutionFactory.create(rule.getQuery(), entity.getModel()));

                    /*
                    System.out.print("# ran query on " + entity.getURI() + ". resulting ESEs: ");
                    for(String e: slses.keySet()) {
                        System.out.print(e + " ");
                    }
                    System.out.println();
                    */

                    // construct / add to resulting slses
                    if(slses.containsKey(entity.getURI())) {
                        //System.out.print("# " + entity.getURI() + " is in: ");
                       
                        for(String uri: slses.get(entity.getURI())) {
                            //System.out.println(uri + " ");
                            if(!slseCache.get(uri).containsElementEntity(entity.getURI())) {
                                slseCache.get(uri).addElementEntity(entity);
                            }
                        }
                        //System.out.println();
                    }
                }
            }

            log("end_add_rule " + name);
        }
	}
}

