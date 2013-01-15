package eu.spitfire_project.smart_service_proxy.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.OptionRegistry;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.TimeProvider.SimulatedTimeUpdater;
import eu.spitfire_project.smart_service_proxy.utils.TList;
import eu.spitfire_project.smart_service_proxy.visualization.VisualizerClient;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 20.10.12
 * Time: 16:15
 * To change this template use File | Settings | File Templates.
 */
public class AutoAnnotation extends CoapClientApplication implements Runnable {

    private static AutoAnnotation instance = new AutoAnnotation();

    private Logger log = Logger.getLogger(AutoAnnotation.class.getName());
    private VisualizerClient visualizerClient;

    //ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);
    public TList sensors = new TList();

    public ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private int updateRate = 2000; //2 second
    private long annotationPeriod = 9 * updateRate * 4;//number hours to trigger annotation
    private long timeCounter = System.currentTimeMillis();
    //private long annotationPeriod;
    private int nnode = 0;



    private AutoAnnotation() {
//        simTime = 360;
//        imgIndex = 24;
//        currentTemperature = 20;
    }

    public void start(){

        run();

        System.out.println("Press a button to start the simulation: ");
        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException e) {
            log.error("This should never happen.", e);
        }

        executorService.scheduleAtFixedRate(this, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    public static AutoAnnotation getInstance(){
        return instance;
    }

    @Override
    public void run() {

        collectDataForAutoAnnotation();

        if(visualizerClient != null){
            log.debug("Invoke visualizer client to request a new image.");
            HttpResponse response = visualizerClient.call();
            log.debug("Visualizer response: " + response);
        }
    }

    private void collectDataForAutoAnnotation() {

        log.debug("Start data collection for auto annotation.");
        try {


//            if (System.currentTimeMillis()-timeCounter > 5*1000 && nnode < 3) {
//                String ipv6 = "sdfsfsdfsfsdf"+String.valueOf(nnode+1);
//
//                String macAddr = "";
//                if (nnode==0) macAddr += "8e7f";
//                else
//                if (nnode==1)  macAddr += "2304";
//                else
//                if (nnode==2)  macAddr += "8e84";
//
//                updateDB(ipv6, macAddr, "http");
//                timeCounter = System.currentTimeMillis();
//                nnode++;
//            }

            //Crawl sensor readings
            for (int i=0; i<sensors.len(); i++) {
                log.debug("Crawling for sensor "+((SensorData)sensors.get(i)).macAddr);
                ((SensorData) sensors.get(i)).crawl();
            }

            //Check if annotation timer of sensors expire then trigger annotation process
            for (int i=0; i<sensors.len(); i++) {
                SensorData sd = (SensorData)sensors.get(i);
                if ("Unannotated".equalsIgnoreCase(sd.FOI)) {
                    log.debug("--------------------------------------- LEVEL 1 ------------------------------------------");
                    long thre = System.currentTimeMillis() - sd.annoTimer;
                    //Trigger annotation here
                    if (thre > annotationPeriod) {
                        log.debug("--------------------------------------- LEVEL 2 ------------------------------------------");
                        //Calculate fuzzy set of other sensors
                        for (int j = 0; j<sensors.len(); j++) {
                            SensorData sensorData = (SensorData) sensors.get(j);
                            if (!"Unannotated".equalsIgnoreCase(sensorData.FOI)) {
                                log.debug("Computing fuzzy set for sensor ("+sensorData.ipv6Addr+", "+sensorData.FOI+") ... ");
                                sensorData.computeFuzzySet(sensorData.getValues().size());
                                log.debug(" Done!");
                            }
                        }

                        //Search for annotation
                        log.debug("Search for annotation... ");
                        double maxsc = 0;
                        String anno = "";
                        for (int j = 0; j < sensors.len(); j++) {
                            SensorData de = (SensorData)sensors.get(j);
                            if (!"Unannotated".equalsIgnoreCase(de.FOI)) {
                                double sc = calculateScore(sd.getValues(), de.getFZ(), de.getDFZ());
                                if (maxsc < sc) {
                                    maxsc = sc;
                                    anno = de.FOI;
                                }
                                log.debug("Similarity to " + de.ipv6Addr + " in " + de.FOI + " is "
                                        + String.format(Locale.GERMANY, "%.10f", sc));
                            }
                        }
                        sd.FOI = anno;
                        log.debug("Resulting annotation is " + anno);

                        //Send POST to sensor
                        String foi = "";
                        if (sd.FOI.equalsIgnoreCase("Living-Room")) foi = "livingroom";
                        else
                        if (sd.FOI.equalsIgnoreCase("Kitchen")) foi = "kitchen";
                        CoapRequest annotation = createCoapRequest(sd.ipv6Addr, foi);
                        log.debug("Sending POST request to sensor!");
                        writeCoapRequest(annotation);
                    }
                }
            }

            //Update current temperature from triple store here

            //Increase simulation time
//            int simTimeM = (int) simTime % 60;
//            int simTimeH = (int) ((double)(simTime)/(double)60) % 24;
//            if (simTimeH==20 && simTimeM==0) {
//                simTime += 10*4*realTimeTick; //9 hours
//                imgIndex = 6*4-1;
//            }
//            simTime += realTimeTick;
//
//            //Update the index of rendered image
//            imgIndex++;
//            if (imgIndex >= numberOfImagesPerDay)
//                imgIndex = 0;

        } catch (Exception e) {
            log.warn("Exception while collecting data for auto annotation: " + e, e);
        }

        log.debug("End data collection for auto annotation.");
    }

    public void setVisualizerClient(VisualizerClient visualizerClient){
        this.visualizerClient = visualizerClient;
    }

    private SensorData findSensorData(String macAddr) {
        SensorData sensorData = null;
        int ind = 0;
        for (; ind<sensors.len(); ind++) {
            SensorData sd = (SensorData) sensors.get(ind);
            if (sd.macAddr.equalsIgnoreCase(macAddr)) {
                sensorData = sd;
                break;
            }
        }
        return sensorData;
    }

    private double calculateScore(List<Double> dataList, FuzzyRule rule, FuzzyRule ruleD) {
        double sc = 0;
        if (rule != null && ruleD != null) {
            if (rule.size() < 2) {
                throw new RuntimeException("Rule size too small");
            }
            if (ruleD.size() < 2) {
                throw new RuntimeException("Rule derivative size too small");
            }

            double rawMax = Collections.max(dataList);
            double rawMin = Collections.min(dataList);

            ArrayList<Double> xList = rule.getxList();
            ArrayList<Double> yList = rule.getyList();
            ArrayList<Double> xListD = ruleD.getxList();
            ArrayList<Double> yListD = ruleD.getyList();
            double ruleRMax = rule.getrMax();
            double ruleRMin = rule.getrMin();

            double us = dataList.size()-1;
            double drange = Math.abs(rawMin-ruleRMin) + Math.abs(rawMax-ruleRMax);

            for (int i = 0; i < dataList.size()-1; i++) {
                double dataValue = dataList.get(i);
                double tmp = dataList.get(i+1);
                double deriValue = tmp - dataValue;
                double scv = 0;
                double scd = 0;

                //Calculate score for value fuzzy set
                int p1 = Collections.binarySearch(xList, dataValue);
                int p2 = 0;
                if (p1 >= 0 ) {
                    // found
                    scv = yList.get(p1);
                } else {
                    // not found
                    p1 = -p1 - 1;
                    if (p1 == 0) {
                        // smaller than min
                    } else if (p1 == rule.size()) {
                        // bigger than max
                        //					p1 = p1 - 2;
                    } else {
                        // data value between rule range
                        p1--;
                        p2 = p1 + 1;
                        double x1 = xList.get(p1);
                        double x2 = xList.get(p2);
                        double y1 = yList.get(p1);
                        double y2 = yList.get(p2);
                        scv = (x1*y2-y1*x2)/(x1-x2) + (y2-y1)/(x2-x1)*dataValue;
                    }
                }

                //Calculate score for derivative fuzzy set
                p1 = Collections.binarySearch(xListD, deriValue);
                p2 = 0;
                if (p1 >= 0 ) {
                    // found
                    scd = yListD.get(p1);
                } else {
                    // not found
                    p1 = -p1 - 1;
                    if (p1 == 0) {
                        // smaller than min
                    } else if (p1 == ruleD.size()) {
                        // bigger than max
                        //					p1 = p1 - 2;
                    } else {
                        // data value between rule range
                        p1--;
                        p2 = p1 + 1;
                        double x1 = xListD.get(p1);
                        double x2 = xListD.get(p2);
                        double y1 = yListD.get(p1);
                        double y2 = yListD.get(p2);
                        scd = (x1*y2-y1*x2)/(x1-x2) + (y2-y1)/(x2-x1)*deriValue;
                    }
                }

                //Fuzzy rule's "and"-operator
                sc += scv*scd;
            }
            sc /= drange*us*us;
        }

        return sc;
    }

    public void updateDB(String ipv6Addr, String macAddr, String httpRequestUri) {
        //Feature of interest
        String FOI = "";
        if ("8e84".equalsIgnoreCase(macAddr)) {
            FOI = "Unannotated";
            log.debug("new node added");
        } else
        if ("2304".equalsIgnoreCase(macAddr) || "a88".equalsIgnoreCase(macAddr)) FOI = "Kitchen";
        else
        if ("8e7f".equalsIgnoreCase(macAddr) || "8ed8".equalsIgnoreCase(macAddr)) FOI = "Living-Room";

        SensorData sd = findSensorData(macAddr);
        if (sd == null)
            sensors.enList(new SensorData(ipv6Addr, macAddr, httpRequestUri, FOI));
        else
        if ("8e84".equalsIgnoreCase(macAddr)) {
            sensors.remove(sd);
            sensors.enList(new SensorData(ipv6Addr, macAddr, httpRequestUri, FOI));
        }
    }

    private CoapRequest createCoapRequest(String ipv6Addr, String resultAnnotation) throws URISyntaxException, ToManyOptionsException, InvalidOptionException, InvalidMessageException, MessageDoesNotAllowPayloadException {
        URI AnnotationServiceURI = new URI("coap://" + ipv6Addr + ":5683/rdf");
        CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.POST, AnnotationServiceURI);
        coapRequest.setContentType(OptionRegistry.MediaType.APP_N3);
        String payloadStr = "\0<coap://"+ipv6Addr+"/rdf>\0" +
                "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\0" +
                "<http://spitfire-project.eu/foi/"+resultAnnotation+">\0" +
                "<http://spitfire-project.eu/foi/"+resultAnnotation+">\0" +
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\0" +
                "<http://spitfire-project.eu/foi/Room>\0";
        byte[] payload = payloadStr.getBytes(Charset.forName("UTF-8"));
        coapRequest.setPayload(payload);

        return coapRequest;
    }




}

