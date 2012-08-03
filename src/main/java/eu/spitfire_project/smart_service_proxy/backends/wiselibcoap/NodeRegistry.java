package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import com.google.common.collect.HashBiMap;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 23.07.12
 * Time: 09:50
 * To change this template use File | Settings | File Templates.
 */
public class NodeRegistry {

    private HashBiMap<Integer,String> registry; //maps nodeId to http URL

    public NodeRegistry(){
        registry = HashBiMap.create();
    }


    public String getUrlAddress(Integer nodeId){
        return registry.get(nodeId);
    }

    public int getNodeID(String URL){
        if(registry.inverse().containsKey(URL))
            return registry.inverse().get(URL);
        else
            return -1;
    }

    public void addNode(Integer nodeId, String URL){
        registry.put(nodeId,URL);
    }
    public void removeNodeByID(Integer nodeId){
        registry.remove(nodeId);
    }

    public void removeNodeByURL(String URL){
        registry.inverse().remove(URL);
    }
}
