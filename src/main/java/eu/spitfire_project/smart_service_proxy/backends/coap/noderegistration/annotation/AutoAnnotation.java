package eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration.annotation;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.OptionRegistry;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.utils.TList;
import eu.spitfire_project.smart_service_proxy.visualization.VisualizerClient;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
    private SensorData unannoSensor = null;

    //ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);
    public TList sensors = new TList();

    public ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private int updateRate = 2000; //2 second
    private long annotationPeriod = 9 * updateRate * 4;//number hours to trigger annotation

    //The currently annotation, it is constantly changed over time
    private String liveAnno = "";

    //The macAddress of the sensors
    private final String NewSensor = "a6c";
    private final String LivingRoomSensor1 = "8e7f";
    private final String LivingRoomSensor2 = "8ed8";
    private final String BedroomSensor1 = "a88";
    private final String BedroomSensor2 = "2304";

    private AutoAnnotation() {
//        simTime = 360;
//        imgIndex = 24;
//        currentTemperature = 20;
    }

    public void start(){
        //run();
        /*
        System.out.println("Press a button to start the simulation: ");
        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException e) {
            log.error("This should never happen.", e);
        }*/

        unannoSensor = null;
        executorService.scheduleAtFixedRate(this, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    public static AutoAnnotation getInstance(){
        return instance;
    }

    @Override
    public void run() {
        if (sensors.len() > 0) {
            collectDataForAutoAnnotation();

            if(visualizerClient != null){
                log.debug("Invoke visualizer client to request a new image.");
                HttpResponse response = visualizerClient.call();
                log.debug("Visualizer response: " + response);
            }
        }
    }

    private void collectDataForAutoAnnotation() {
        log.debug("Start data collection for auto annotation.");
        try {
            //Crawl sensor readings
            for (int i=0; i<sensors.len(); i++) {
                log.debug("Crawling for sensor "+((SensorData)sensors.get(i)).macAddr);
                ((SensorData) sensors.get(i)).crawl();
            }

            //Update fuzzysets of sensors
            for (int j = 0; j<sensors.len(); j++) {
                SensorData sensorData = (SensorData) sensors.get(j);
                log.debug("Computing fuzzy set for sensor ("+sensorData.macAddr+", "+sensorData.FOI+") ... ");
                //sensorData.computeFuzzySet(sensorData.getValues().size());
                sensorData.computeFuzzySet(15); //latest 15 points
                ArrayList<Double> data = (ArrayList<Double>) sensorData.getValues();
                /*for (int k=0; k<data.size(); k++)
                    System.out.print(", "+String.format(Locale.US, "%.2f", sensorData.getValues().get(k)));
                System.out.println();
                if (sensorData.getFZ() != null) {
                    for (int l=0; l<sensorData.getFZ().size(); l++) {
                        double x = sensorData.getFZ().getxList().get(l);
                        double y = sensorData.getFZ().getyList().get(l);
                        System.out.print(", "+String.format(Locale.US, "(%.2f, %.2f)", x, y));
                    }
                    System.out.println();
                }*/
            }

            //Search for annotation as sensor readings are being updated, until the unannotated sensor is annotated
            if (unannoSensor != null) {
                if ("Unannotated".equalsIgnoreCase(unannoSensor.FOI)) {
                    double maxsc = 0;
                    for (int j = 0; j < sensors.len(); j++) {
                        SensorData de = (SensorData)sensors.get(j);
                        if (!"Unannotated".equalsIgnoreCase(de.FOI)) {
                            double sc = calculateScore(unannoSensor.getFZ(), de.getFZ(), 100);
                            de.liveSc = sc;
                            if (maxsc < sc) {
                                maxsc = sc;
                                liveAnno = de.FOI;
                            }
                            log.debug("Similarity to " + de.macAddr + " in " + de.FOI + " is "
                                    + String.format(Locale.GERMANY, "%.10f", sc));
                        }
                    }
                    log.debug("Live annotation is " + liveAnno);

                    //Check if it is the time to finalize annotation and send it to the unannotated sensor via COAP
                    long thre = System.currentTimeMillis() - unannoSensor.annoTimer;
                    if (thre > annotationPeriod) {
                        //Send POST to sensor
                        String foi = "";
                        //if ("LivingRoom".equalsIgnoreCase(liveAnno)) foi = "livingroom";
                        //else if ("Kitchen".equalsIgnoreCase(liveAnno)) foi = "bedroom";
                        unannoSensor.FOI = liveAnno;
                        CoapRequest annotation = createCoapRequest(unannoSensor.ipv6Addr, foi);
                        log.debug("Sending POST request to sensor!");
                        writeCoapRequest(annotation);
                    }
                }
            }
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

    private double calculateScore(FuzzyRule ruleC, FuzzyRule rule, int nPoint) {
        double sc = 0;
        //log.debug("In here 0");
        if (ruleC==null || rule==null) return 0;
        //log.debug("In here 1");
        if (rule.size()<2 || ruleC.size()<2) return 0;
        //log.debug("In here 2");

        //If two rules do not overlap then sc = 0
        if (rule.getrMax()<ruleC.getrMin() || ruleC.getrMax()<rule.getrMin()) return 0;


        //Find the union range
        double xumin = ruleC.getrMin();
        if (xumin > rule.getrMin())
            xumin = rule.getrMin();
        double xumax = ruleC.getrMax();
        if (xumax < rule.getrMax())
            xumax = rule.getrMax();

        //Find the overlapping range
        double xomin = ruleC.getrMin();
        if (xomin < rule.getrMin())
            xomin = rule.getrMin();
        double xomax = ruleC.getrMax();
        if (xomax > rule.getrMax())
            xomax = rule.getrMax();

        double min = ruleC.getrMax()-ruleC.getrMin();
        double max = ruleC.getrMax()-ruleC.getrMin();
        if (min > rule.getrMax()-rule.getrMin()) min = rule.getrMax()-rule.getrMin();
        if (max < rule.getrMax()-rule.getrMin()) max = rule.getrMax()-rule.getrMin();
        double delta = min/max;

        //calculate the score
        double step = (xomax-xomin)/nPoint;
        for (int i=0; i<nPoint; i++) {
            double eval = rule.evaluate(xomin+i*step);
            double evalC = ruleC.evaluate(xomin+i*step);
            sc += Math.abs(eval-evalC);
        }
        sc = sc/nPoint*(xomax-xomin)/(xumax-xumin);
        //sc = sc*delta/nPoint;

        return sc;
    }

    public String getLiveAnno() {
        return liveAnno;
    }

    private String assignFOI(String macAddr) {
        String foi = null;

        if (NewSensor.equalsIgnoreCase(macAddr)) foi = "Unannotated";
        else if (BedroomSensor1.equalsIgnoreCase(macAddr) || BedroomSensor2.equalsIgnoreCase(macAddr)) foi = "Bedroom";
        else if (LivingRoomSensor1.equalsIgnoreCase(macAddr) || LivingRoomSensor2.equalsIgnoreCase(macAddr)) foi = "LivingRoom";

        return foi;
    }

    public void addNewEntryToDB(String ipv6Addr, String macAddr, String httpRequestUri) {
        //Check if the sensor with macAddr is already in the DB?
        SensorData sd = findSensorData(macAddr);

        if (liveAnno == "")
            liveAnno = "Unannotated";

        //If the new sensor is the "new sensor", then assign unannoSensor to it
        if (NewSensor.equalsIgnoreCase(macAddr)) {
            unannoSensor = new SensorData(ipv6Addr, macAddr, httpRequestUri, assignFOI(macAddr));
            liveAnno =  "Unannotated";
        }

        //If sensor is already in DB, then delete it, and add it again later on
        if (sd != null)
            sensors.remove(sd);

        //Add this sensor to DB
        if (NewSensor.equalsIgnoreCase(macAddr)) {
            sensors.enList(unannoSensor);
            liveAnno =  "Unannotated";
        } else
            sensors.enList(new SensorData(ipv6Addr, macAddr, httpRequestUri, assignFOI(macAddr)));
        log.debug("new node added: "+macAddr);
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


    @Override
    public void receiveResponse(CoapResponse coapResponse) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void receiveEmptyACK() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleRetransmissionTimout() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
