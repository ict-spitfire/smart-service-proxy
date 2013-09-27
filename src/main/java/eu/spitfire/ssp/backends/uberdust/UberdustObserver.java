package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.AbstractResourceObserver;
import eu.spitfire.ssp.backends.LocalPipelineFactory;
import eu.spitfire.ssp.backends.coap.CoapResourceToolbox;
import eu.spitfire.ssp.backends.coap.ResourceToolBox;
import eu.uberdust.communication.UberdustClient;
import eu.uberdust.communication.protobuf.Message;
import eu.uberdust.communication.websocket.readings.WSReadingsClient;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
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
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final HashMap<String, String> testbeds;
    public Map<URI, UberdustNode> allnodes;

    private final UberdustBackendManager serviceManager;

    public UberdustObserver(UberdustBackendManager serviceManager,
                            ScheduledExecutorService scheduledExecutorService,
                            LocalPipelineFactory localChannel) throws IOException {


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

        (new Thread() {
            @Override
            public void run() {
                super.run();    //To change body of overridden methods use File | Settings | File Templates.
                if (!(o instanceof WSReadingsClient)) {
                    return;
                }
                if (arg instanceof Message.NodeReadings) {
                    Message.NodeReadings.Reading reading = ((Message.NodeReadings) arg).getReading(0);
//                    log.info("mnode:" + reading.getNode());
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

                            final Map<URI, Model> modelsMap = CoapResourceToolbox.getModelsPerSubject(allnodes.get(resourceURI).getModel());
                            for (final URI uri : modelsMap.keySet()) {
                                registerResource(uri, modelsMap.get(uri));
//                                removeResource(uri);
                                cacheResourceStatus(uri, modelsMap.get(uri));
                            }

                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }
        }).start();

    }

    private void registerResource(final URI resourceUri, final Model model) throws URISyntaxException {
        final Map<URI, Model> modelsMap = CoapResourceToolbox.getModelsPerSubject(model);
        for (final URI uri : modelsMap.keySet()) {

            final SettableFuture<URI> resourceRegistrationFuture = serviceManager.registerResource(uri);
            resourceRegistrationFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        //This is just to check if an exception was thrown
                        resourceRegistrationFuture.get();

                        //If there was no exception finalize registration process
                        ChannelFuture future = cacheResourceStatus(uri, modelsMap.get(uri));
                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess())
                                    log.debug("Succesfully stored status of {} in cache.", resourceUri);
                                else
                                    log.error("Failed to store status of {} in cache.", resourceUri);
                            }
                        });


                    } catch (Exception e) {
                        log.warn("Exception while registering resources.", e);
                    }
                }
            }, getScheduledExecutorService());
        }
    }


}