package eu.spitfire.ssp.backends.uberdust.job;

import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.backends.uberdust.UberdustNodeHelper;
import eu.spitfire.ssp.backends.uberdust.UberdustObserver;
import eu.uberdust.communication.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: amaxilatis
 * Date: 10/12/13
 * Time: 1:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpdateJob implements Runnable {
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final URI resourceURI;
    private final UberdustObserver observer;
    private Message.NodeReadings.Reading reading;

    public UpdateJob(UberdustObserver observer, Message.NodeReadings.Reading reading, URI resourceURI) {
        this.resourceURI = resourceURI;
        this.reading = reading;
        this.observer = observer;
    }

    @Override
    public void run() {
        log.debug("Received  Update with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift. " + Thread.activeCount() + " Threads Running.");
        try {
            final Statement valueStatement = UberdustNodeHelper.createUpdateValueStatement(resourceURI, reading.getDoubleReading());
            final Statement timeStatement = UberdustNodeHelper.createUpdateTimestampStatement(resourceURI, new Date(reading.getTimestamp()));
            observer.updateResourceStatus(valueStatement);
            observer.updateResourceStatus(timeStatement);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        log.warn("Processed Update with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift.");
    }
}
