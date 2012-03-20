package eu.spitfire_project.smart_service_proxy.backends.uberdust;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 17:04
 * To change this template use File | Settings | File Templates.
 */
public class Capability {
	private String uomInUse;
	private String observedProperty;

	Capability(String observedProperty, String uomInUse) {
		this.observedProperty = observedProperty;
		this.uomInUse = uomInUse;
	}

	String getUomInUse() { return uomInUse; }
	String getObservedProperty() { return observedProperty; }
}
