package eu.spitfire.ssp.server.internal.messages.requests;

import com.hp.hpl.jena.rdf.model.RDFNode;

import java.net.URI;
import java.util.Date;

/**
 * Created by olli on 12.07.14.
 */
public class SensorValueUpdate {

    private final URI sensorGraphName;
    private final RDFNode value;
    private final Date expiry;

    public SensorValueUpdate(URI sensorGraphName, RDFNode value, Date expiry){

        this.sensorGraphName = sensorGraphName;
        this.value = value;
        this.expiry = expiry;
    }

    public URI getSensorGraphName() {
        return sensorGraphName;
    }

    public RDFNode getValue() {
        return value;
    }

    public Date getExpiry() {
        return expiry;
    }
}
