package eu.spitfire_project.smart_service_proxy.backends.uberdust;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 16:58
 * To change this template use File | Settings | File Templates.
 */
public class CapabilityMap {

	private Map<String, Capability> capabilities;
	private static CapabilityMap instance_;

	private CapabilityMap() {
		capabilities = new HashMap<String, Capability>();
	}

	public static CapabilityMap getInstance() {
		if(instance_ == null) {
			instance_ = new CapabilityMap();
		}
		return instance_;
	}
	
	void add(String urn, Capability capability) {
		capabilities.put(urn, capability);
	}
	
	Capability getByURN(String urn) {
		return capabilities.get(urn);
	}
}
