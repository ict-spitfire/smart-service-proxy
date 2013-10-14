package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.backends.generic.BackendResourceManager;
import eu.spitfire.ssp.backends.generic.DataOriginObserver;
import eu.spitfire.ssp.backends.uberdust.job.InsertJob;
import eu.spitfire.ssp.backends.uberdust.job.UpdateJob;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import eu.uberdust.communication.UberdustClient;
import eu.uberdust.communication.protobuf.Message;
import eu.uberdust.communication.websocket.readings.WSReadingsClient;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Opens a WebSocket connection to Uberdust monitoring all requested sensor reading from it.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustObserver extends DataOriginObserver implements Observer {
    private static String UBERDUST_URL;
    private static String UBERDUST_URL_WS_PORT;
    private static String UBERDUST_OBSERVE_NODES;
    private static String UBERDUST_OBSERVE_CAPABILITIES;
    //    private final ScheduledExecutorService scheduledExecutorService;
    private final LocalPipelineFactory localChannel;
    private final BackendResourceManager<URI> backendResourceManager;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final HashMap<String, String> testbeds;

    private final Executor insertExecutor;
    private final Executor updateExecutor;

//    private final Map<URI, URI> tinyURIS;

    private final UberdustBackendComponentFactory serviceManager;

    public UberdustObserver(UberdustBackendComponentFactory backendComponentFactory,
                            ScheduledExecutorService scheduledExecutorService,
                            LocalPipelineFactory localChannel, int observerInsetThreadCount) throws IOException {
        super(backendComponentFactory);
//        executor = scheduledExecutorService;
        ThreadFactory insertTf = new ThreadFactoryBuilder().setNameFormat("UberdustInsert #%d").build();
        ThreadFactory updateTf = new ThreadFactoryBuilder().setNameFormat("UberdustUpdate #%d").build();
        insertExecutor = Executors.newFixedThreadPool(observerInsetThreadCount, insertTf);
        updateExecutor = Executors.newSingleThreadExecutor(updateTf);


        this.localChannel = localChannel;

//        tinyURIS = new HashMap<>();
        backendResourceManager = backendComponentFactory.getBackendResourceManager();

        this.serviceManager = backendComponentFactory;

        testbeds = new HashMap<String, String>();
        testbeds.put("urn:wisebed:ctitestbed:", "1");
        testbeds.put("urn:santander:", "2");
        testbeds.put("urn:ctinetwork:", "3");
        testbeds.put("urn:ctibuilding:", "4");
        testbeds.put("urn:pspace:", "5");
        testbeds.put("urn:testing:", "6");
        testbeds.put("urn:amaxilat:", "7");
        testbeds.put("urn:gen6:", "8");


        Configuration config = null;
        try {
            config = new PropertiesConfiguration("ssp.properties");
            UBERDUST_URL = config.getString("UBERDUST_URL", "uberdust.cti.gr");
            UBERDUST_URL_WS_PORT = config.getString("UBERDUST_URL_WS_PORT", "80");
            UBERDUST_OBSERVE_NODES = config.getString("UBERDUST_OBSERVE_NODES", "*");
            UBERDUST_OBSERVE_CAPABILITIES = config.getString("UBERDUST_OBSERVE_CAPABILITIES", "*");

            UberdustClient.setUberdustURL("http://" + UBERDUST_URL);
            final String webSocketUrl = "ws://" + UBERDUST_URL + ":" + UBERDUST_URL_WS_PORT + "/readings.ws";
            WSReadingsClient.getInstance().setServerUrl(webSocketUrl);
            final String[] nodelist = UBERDUST_OBSERVE_NODES.split("\\+");
            for (String nodes : nodelist) {
                log.info("Subscribing to : " + nodes);
                WSReadingsClient.getInstance().subscribe(nodes, UBERDUST_OBSERVE_CAPABILITIES);
            }
            WSReadingsClient.getInstance().addObserver(this);

        } catch (ConfigurationException e) {
            log.error("Could not read configuration file ssp.properties !");
        }


    }


    public void update(final Observable o, final Object arg) {
        //log.info("Running thread count: " + Thread.activeCount());
        if (!(o instanceof WSReadingsClient)) {
            return;
        }
        if (arg instanceof Message.NodeReadings) {
            final Message.NodeReadings.Reading reading = ((Message.NodeReadings) arg).getReading(0);
//                            log.info(reading.toString());
//                            log.error((System.currentTimeMillis() - reading.getTimestamp()) + " Adiff in millis " + reading.getNode() + " " + reading.getCapability());
            if (reading.hasDoubleReading()) {
                if (reading.getNode().contains("santander")) return;
                if (!reading.getCapability().contains("urn")) return;
                if (reading.getCapability().contains("parent")) return;
                if (reading.getCapability().contains("report")) return;
                if (!reading.getCapability().startsWith("urn")) return;
                if (
                        !reading.getCapability().contains("temperature")
                                && !reading.getCapability().contains("light")
                                && !reading.getCapability().contains("kwh")
                                && !reading.getCapability().contains("lz")
                                && !reading.getCapability().endsWith("r")
                                && !reading.getCapability().endsWith("s")
                                && !reading.getCapability().endsWith("ac")
                        ) return;
//                            if (!reading.getNode().contains("u")) return;
//                            log.info("mnode2:" + reading.getNode());


                String currentPrefix = null;
                for (String aprefix : testbeds.keySet()) {
                    if (reading.getNode().contains(aprefix)) {
                        currentPrefix = aprefix;
                    }
                }
                final String prefix = currentPrefix;

                try {

                    final URI resourceURI = new URI(UberdustNodeHelper.getResourceURI(testbeds.get(prefix), reading.getNode(), reading.getCapability()));

                    final Collection<URI> collection = backendResourceManager.getResources(resourceURI);
                    if (collection.isEmpty()) {
                        insertExecutor.execute(new InsertJob(this, reading, testbeds.get(prefix), prefix));
                    } else {
                        updateExecutor.execute(new UpdateJob(this, reading, resourceURI));
                    }

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }
        }


    }

    public final void doCacheResourcesStates(Model model) {
        cacheResourcesStates(model);
    }

    public final void updateResourceStatus(Statement statement) {
        updateResourceStatus(statement, null);
    }


    public void registerModel(final Model model, String resourceURI) throws URISyntaxException {
        final Map<URI, Model> modelsMap = CoapResourceToolbox.getModelsPerSubject(model);

        for (final URI uri : modelsMap.keySet()) {

            if (uri.toString().contains("attachedSystem")) {
                final URI tinyURI = new URI("http://uberdust.cti.gr/" + UberdustNodeHelper.tiny(resourceURI));
//                tinyURIS.put(tinyURI, uri);
                serviceManager.registerResource(modelsMap.get(uri), tinyURI);
            }
            serviceManager.registerResource(modelsMap.get(uri), uri);
        }
    }


}