package eu.spitfire_project.smart_service_proxy.backends.uberdust;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 23.01.12
 * Time: 17:21
 * To change this template use File | Settings | File Templates.
 */
public class Observation {

	private Long timestamp;
	private String value;

	public Observation(Long timestamp, String value) {
		this.timestamp = timestamp;
		this.value = value;
	}

	public static Observation fromLine(String obs) {
		String[] parts = obs.split("\\s");
		Long timestamp = Long.parseLong(parts[0]);
		String value = parts[1];
		return new Observation(timestamp, value);
	}

	public String getValue() {
		return value;
	}

	public Long getTimestamp() {
		return timestamp;
	}
}
