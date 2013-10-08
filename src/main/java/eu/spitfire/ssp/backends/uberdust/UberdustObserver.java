package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.LocalPipelineFactory;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.server.pipeline.handler.cache.SemanticCache;
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
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Opens a WebSocket connection to Uberdust monitoring all requested sensor reading from it.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustObserver implements Observer {
    private static String UBERDUST_URL;
    private static String UBERDUST_URL_WS_PORT;
    private static String UBERDUST_OBSERVE_NODES;
    private static String UBERDUST_OBSERVE_CAPABILITIES;
    private final ScheduledExecutorService scheduledExecutorService;
    private final LocalPipelineFactory localChannel;
    private final SemanticCache semanticCache;
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final HashMap<String, String> testbeds;
    public Map<URI, UberdustNode> allnodes;

    private final Executor executor;

    private final UberdustBackendManager serviceManager;

    public UberdustObserver(UberdustBackendManager serviceManager,
                            ScheduledExecutorService scheduledExecutorService,
                            LocalPipelineFactory localChannel, SemanticCache semanticCache) throws IOException {
        this.semanticCache = semanticCache;
        this.scheduledExecutorService = scheduledExecutorService;
        this.localChannel = localChannel;

        this.serviceManager = serviceManager;
        allnodes = new HashMap<URI, UberdustNode>();

        testbeds = new HashMap<String, String>();
        testbeds.put("urn:wisebed:ctitestbed:", "1");
        testbeds.put("urn:santander:", "2");
        testbeds.put("urn:ctinetwork:", "3");
        testbeds.put("urn:ctibuilding:", "4");
        testbeds.put("urn:pspace:", "5");
        testbeds.put("urn:testing:", "6");
        testbeds.put("urn:amaxilat:", "7");
        testbeds.put("urn:gen6:", "8");

        executor = Executors.newCachedThreadPool();

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
        executor.execute(
                new Thread() {
                    @Override
                    public void run() {
                        super.run();    //To change body of overridden methods use File | Settings | File Templates.
                        if (!(o instanceof WSReadingsClient)) {
                            return;
                        }
                        if (arg instanceof Message.NodeReadings) {
                            Message.NodeReadings.Reading reading = ((Message.NodeReadings) arg).getReading(0);
                            log.info(reading.toString());
                            if (reading.hasDoubleReading()) {
                                try {
                                    if (reading.getNode().contains("santander")) return;
                                    if (!reading.getCapability().contains("urn")) return;
                                    if (reading.getCapability().contains("parent")) return;
                                    if (reading.getCapability().contains("report")) return;
//                            if (!reading.getNode().contains("u")) return;
//                            log.info("mnode2:" + reading.getNode());
                                    String prefix = "";
                                    for (String aprefix : testbeds.keySet()) {
                                        if (reading.getNode().contains(aprefix)) {
                                            prefix = aprefix;
                                        }
                                    }


                                    final URI resourceURI = new URI(UberdustNode.getResourceURI(testbeds.get(prefix), reading.getNode(), reading.getCapability()));

                                    if (!allnodes.containsKey(resourceURI)) {
                                        allnodes.put(resourceURI, new UberdustNode(reading.getNode(), testbeds.get(prefix), prefix, reading.getCapability(), reading.getDoubleReading(), new Date(reading.getTimestamp())));
                                    } else {
                                        allnodes.get(resourceURI).update(reading.getDoubleReading(), new Date(reading.getTimestamp()));
                                    }
                                    registerModel(allnodes.get(resourceURI).getModel());

                                } catch (Exception e) {
                                    log.error(e.getMessage(), e);
                                }
                            }
                        }
                    }
                });

    }

    private void registerModel(final Model model) throws URISyntaxException {
//        StmtIterator stmp = model.listStatements();

        new Thread() {
            @Override
            public void run() {
                try {
                    final Map<URI, Model> modelsMap = CoapResourceToolbox.getModelsPerSubject(model);
                    for (final URI uri : modelsMap.keySet()) {
                        serviceManager.registerResource(uri);
                        semanticCache.putResourceToCache(uri, modelsMap.get(uri), new Date(System.currentTimeMillis() + 99 * 60 * 60 * 1000));
                    }
                } catch (URISyntaxException e) {
                    log.debug("This should never happen", e);
                }
            }
        }.start();
    }


}