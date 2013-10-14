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
        long start = 0;
        log.warn("Received Update with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift. " + Thread.activeCount() + " Threads Running.");
        try {
            start = System.currentTimeMillis();
            final Statement valueStatement = UberdustNodeHelper.createUpdateValueStatement(resourceURI, reading.getDoubleReading());
            final Statement timeStatement = UberdustNodeHelper.createUpdateTimestampStatement(resourceURI, new Date(reading.getTimestamp()));
            log.warn("uberdustUpdate " + (System.currentTimeMillis() - start) + " millis " + resourceURI.hashCode());
            start = System.currentTimeMillis();
            observer.updateResourceStatus(valueStatement);
            log.warn("jenaUpdate " + (System.currentTimeMillis() - start) + " millis " + resourceURI.hashCode());
            start = System.currentTimeMillis();
            observer.updateResourceStatus(timeStatement);
            log.warn("jenaUpdate " + (System.currentTimeMillis() - start) + " millis " + resourceURI.hashCode());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        log.warn("Processed Update with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift.");
    }
}
