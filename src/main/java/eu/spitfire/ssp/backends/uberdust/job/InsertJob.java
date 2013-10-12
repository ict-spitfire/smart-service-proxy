package eu.spitfire.ssp.backends.uberdust.job;

import eu.spitfire.ssp.backends.uberdust.UberdustNodeHelper;
import eu.spitfire.ssp.backends.uberdust.UberdustObserver;
import eu.uberdust.communication.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: amaxilatis
 * Date: 10/12/13
 * Time: 1:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class InsertJob extends Thread {
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final String prefix;
    private final String testbed;
    private final UberdustObserver observer;
    private Message.NodeReadings.Reading reading;

    public InsertJob(UberdustObserver observer, Message.NodeReadings.Reading reading, String testbed, String prefix) {
        this.prefix = prefix;
        this.testbed = testbed;
        this.reading = reading;
        this.observer = observer;
    }

    @Override
    public void run() {
        log.debug("Received Insert with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift. " + Thread.activeCount() + " Threads Running.");
        try {
            observer.registerModel(UberdustNodeHelper.generateDescription(reading.getNode(), testbed, prefix, reading.getCapability(), reading.getDoubleReading(), new Date(reading.getTimestamp())),
                    UberdustNodeHelper.getResourceURI(testbed, reading.getNode(), reading.getCapability()));
            observer.doCacheResourcesStates(UberdustNodeHelper.generateDescription(reading.getNode(), testbed, prefix, reading.getCapability(), reading.getDoubleReading(), new Date(reading.getTimestamp())));
        } catch (URISyntaxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        log.warn("Processed Insert with " + (System.currentTimeMillis() - reading.getTimestamp()) + " millis drift.");
    }
}
