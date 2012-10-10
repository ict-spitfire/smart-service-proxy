/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire_project.smart_service_proxy.backends.coap;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.application.CoapServerApplication;
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
import eu.spitfire_project.smart_service_proxy.utils.TString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.util.IPAddressUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Oliver Kleine
 */
public class CoapNodeRegistrationServer extends CoapServerApplication {
    private static int numberOfAnnotationDemo = 0;
    private static long currentTime, timeOfLastAnnotation;
    private static long timeThreshold = 10000; //10 seconds

    private static Logger log = LoggerFactory.getLogger(CoapNodeRegistrationServer.class.getName());

    private ArrayList<CoapBackend> coapBackends = new ArrayList<CoapBackend>();

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

    private static CoapNodeRegistrationServer instance = new CoapNodeRegistrationServer();


    private CoapNodeRegistrationServer(){
        super();
        log.debug("Constructed.");

        currentTime = System.currentTimeMillis();
        timeOfLastAnnotation = currentTime;
    }

    public static CoapNodeRegistrationServer getInstance(){
        return instance;
    }

    public boolean addCoapBackend(CoapBackend coapBackend){
        boolean added = coapBackends.add(coapBackend);
        if(added){
            log.debug("Registered new backend for prefix: " + coapBackend.getPrefix());
        }
        return added;
    }

    public void fakeRegistration(InetAddress inetAddress){
        executorService.schedule(new NodeRegistration(inetAddress), 0, TimeUnit.SECONDS);
    }

    /**
     * This method is invoked by the nCoAP framework whenever a new incoming CoAP request is to be processed. It only
     * accepts requests with {@link Code#GET} for the resource /here_i_am. All other requests will cause failure
     * responses ({@link Code#NOT_FOUND_404} for other resources or {@link Code#METHOD_NOT_ALLOWED_405} for
     * other methods).
     * 
     * @param coapRequest
     * @param remoteSocketAddress
     * @return
     */
    @Override
    public CoapResponse receiveCoapRequest(CoapRequest coapRequest, InetSocketAddress remoteSocketAddress) {

        log.debug("[CoapNodeRegistrationServer] Received request from " +
                remoteSocketAddress.getAddress().getHostAddress() + ":" + remoteSocketAddress.getPort()
                + " for resource " + coapRequest.getTargetUri());

        CoapResponse coapResponse = null;

        if(coapRequest.getTargetUri().getPath().equals("/here_i_am")){
            if(coapRequest.getCode() == Code.POST){
                if(coapRequest.getMessageType() == MsgType.CON){
                    coapResponse =  new CoapResponse(MsgType.ACK, Code.CONTENT_205);
                }
                log.debug("[CoapNodeRegistrationServer] Schedule sending of request for .well-known/core");
                executorService.schedule(new NodeRegistration(remoteSocketAddress.getAddress()), 0, TimeUnit.SECONDS);

                //----------- implementation of fuzzy annotation ----------------------
                log.debug("[CoapNodeRegistrationServer] Schedule sensor annotation");
                String sensorMACAddr = "";
                while (coapRequest.getPayload().readable())
                    sensorMACAddr += (char)coapRequest.getPayload().readByte();

                log.debug("Sensor MAC: " + sensorMACAddr);
                if ("0x8e7f".equalsIgnoreCase(sensorMACAddr)) {
                    currentTime = System.currentTimeMillis();

                    log.debug("Current time - old time: "+(currentTime- timeOfLastAnnotation));
                    if (currentTime - timeOfLastAnnotation >= timeThreshold) {
                        executorService.schedule(new NodeAnnotation(remoteSocketAddress.getAddress()), 2, TimeUnit.SECONDS);
                        timeOfLastAnnotation = currentTime;
                    }
                }
            }
            else{
                coapResponse = new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
            }
        }
        else{
            coapResponse = new CoapResponse(Code.NOT_FOUND_404);
        }

        return coapResponse;
    }

    //Handles the registration process for new nodes in a new thread
    private class NodeRegistration extends CoapClientApplication implements Runnable{

        private Inet6Address remoteAddress;

        private Object monitor = new Object();

        private CoapResponse coapResponse;
        
        public NodeRegistration(InetAddress remoteAddress){
            super();
            this.remoteAddress = (Inet6Address) remoteAddress;
        }

        @Override
        public void run(){

            CoapBackend coapBackend = null;

            //log.debug("Look up backend for address " + remoteAddress.getHostAddress());
            for(CoapBackend backend : coapBackends){
                //Prefix is an IP address

                log.debug("remoteAddress.getHostAddress(): " + remoteAddress.getHostAddress());
                log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());
                if(remoteAddress.getHostAddress().startsWith(backend.getPrefix())){
                    coapBackend = backend;
                    log.debug("Backend found for address " + remoteAddress.getHostAddress());
                    break;
                }
                //Prefix is a DNS name
                else{
                    log.debug("Look up backend for DNS name " + remoteAddress.getHostName());
                    log.debug("backend.getIPv6Prefix(): " + backend.getPrefix());
                    if((remoteAddress.getHostName()).equals(backend.getPrefix())){
                        coapBackend = backend;
                        log.debug("Backend found for DNS name " + remoteAddress.getHostName());
                        break;
                    }
                }
            }
            
            if(coapBackend == null){
                log.debug("[CoapNodeRegistrationServer] No backend found for IP address: " +
                        remoteAddress.getHostAddress());
                return;
            }
            
            //Only register new nodes (avoid duplicates)
            Set<Inet6Address> addressList = coapBackend.getSensorNodes();

            if(addressList.contains(remoteAddress)){
                log.debug("New here_i_am message from " + remoteAddress + ".");
                coapBackend.deleteServices(remoteAddress);
            }

            try {
                //Send request to the .well-known/core resource of the new sensornode
                String remoteIP = remoteAddress.getHostAddress();
                //Remove eventual scope ID
                if(remoteIP.indexOf("%") != -1){
                    remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
                }
                if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
                    remoteIP = "[" + remoteIP + "]";
                }
                URI targetURI = new URI("coap://" + remoteIP + ":5683/.well-known/core");
                CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, targetURI, this);

                synchronized (monitor){
                    //Write request for .well-knwon/core
                    writeCoapRequest(coapRequest);
                    if(log.isDebugEnabled()){
                        log.debug("[CoapNodeRegistration] Request for /.well-known/core resource at: " +
                                remoteAddress.getHostAddress() + " written.");
                    }

                    //Wait for the response
                    while(coapResponse == null){
                        monitor.wait();
                    }

                    //Process the response
                    coapBackend.processWellKnownCoreResource(coapResponse, remoteAddress);
                }

            } catch (InvalidMessageException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (ToManyOptionsException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InvalidOptionException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (URISyntaxException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            } catch (InterruptedException e) {
                log.error("[" + this.getClass().getName() + "] " + e.getClass().getName(), e);
            }
        }


        @Override
        public void receiveResponse(CoapResponse coapResponse) {
            log.debug("Received response for well-known/core");
            synchronized (monitor){
                this.coapResponse = coapResponse;
                monitor.notify();
            }
        }
    }

    /* -------------------------------- SENSOR ANNOTATION IMPLEMENTATION ---------------------------------------- */

    private class FuzzyRule {
        private final ArrayList<Double> xList = new ArrayList<Double>();
        private final ArrayList<Double> yList = new ArrayList<Double>();
        private double rMax;
        private double rMin;

        /**
         * @return count of points in the rule.
         */
        public int size() {
            if (xList.size() != yList.size()) {
                throw new RuntimeException("X,Y length not consistent");
            }
            return xList.size();
        }

        /**
         * Add a point as the next point.
         * @param x - x value of the point.
         * @param y - y value of the point.
         */
        public void add(Double x, Double y) {
            xList.add(x);
            yList.add(y);
        }

        /**
         * Primitive version of {@link #add(Double, Double)}.
         */
        public void add(double x, double y) {
            xList.add(x);
            yList.add(y);
        }

        /**
         * Set rule max to the given value.
         * @param rMax - rule max value.
         */
        public void setrMax(double rMax) {
            this.rMax = rMax;
        }

        /**
         * @return rule max value.
         */
        public double getrMax() {
            return rMax;
        }

        /**
         * Set rule min to the given value.
         * @param rMin - rule min value.
         */
        public void setrMin(double rMin) {
            this.rMin = rMin;
        }

        /**
         * @return rule min value.
         */
        public double getrMin() {
            return rMin;
        }

        /**
         * @return list of x value of the points in original order.
         */
        public ArrayList<Double> getxList() {
            return xList;
        }

        /**
         * @return  list of y value of the points in original order.
         */
        public ArrayList<Double> getyList() {
            return yList;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("FuzzyRule [rMax=");
            builder.append(rMax);
            builder.append(", rMin=");
            builder.append(rMin);
            builder.append(", size=");
            builder.append(xList.size());
            builder.append("]");
            return builder.toString();
        }
    }

    private class DataEntry {
        private String MACAddr, semanticDescription;
        private ArrayList<Long> timeStamps = new ArrayList<Long>(); //Time-stamp of a samples
        private ArrayList<Double> values = new ArrayList<Double>(); //Value of the samples
        private FuzzyRule fz, dfz;

        public DataEntry(String MACAddr, String SD) {
            this.MACAddr = MACAddr;
            this.semanticDescription = SD;
            this.timeStamps = new ArrayList<Long>();
            this.values = new ArrayList<Double>();
        }

        public void add(Long ts, Double vl) {
            timeStamps.add(ts);
            values.add(vl);
        }

        public ArrayList<Long> getTimeStamps() {
            return timeStamps;
        }

        public ArrayList<Double> getValues() {
            return values;
        }

        public String getMACAddr() {
            return MACAddr;
        }

        public String getSD() {
            return semanticDescription;
        }

        public void computeFuzzySet() {
            fz = extractRule(values);
            dfz = extractRuleD(values);
        }

        public FuzzyRule getFZ() {
            return fz;
        }

        public FuzzyRule getDFZ() {
            return dfz;
        }

        private FuzzyRule extractRuleD(List<Double> dataList) {
            ArrayList<Double> deriList = new ArrayList<Double>();
            Double[] raw = dataList.toArray(new Double[dataList.size()]);
            for (int i=0; i<raw.length-1; i++) {
                Double tmp = Double.valueOf(raw[i+1]-raw[i]);
                deriList.add(tmp);
            }
            deriList.add(deriList.get(deriList.size()-1));

            return extractRule(deriList);
        }

        private FuzzyRule extractRule(List<Double> dataList) {
            Double[] raw = dataList.toArray(new Double[dataList.size()]);

            // Identify the value range
            double rawMax = Collections.max(dataList);
            double rawMin = Collections.min(dataList);
            double rawRange = rawMax - rawMin;

            // Special case: all values are the same
            if (rawRange <= Double.MIN_VALUE) {
                log.debug("All values in snapshot are identical");
                double epsilon = 0.05;
                FuzzyRule rule = new FuzzyRule();
                rule.setrMax(rawMax + epsilon);
                rule.setrMin(rawMin - epsilon);
                rule.add(raw[0] - epsilon, 1);
                rule.add(raw[0] + epsilon, 1);
                return rule;
            }

            double ra = 0.5*rawRange;
            double alpha = 4/ra/ra;
            double ndc[] = new double[raw.length];

            //Calculate neighborhood density curve of data
            for (int i=0; i<raw.length; i++) {
                ndc[i] = 0;
                //double xi = 2*normRaw[i]-normRaw[i-1];
                double xi = raw[i];
                for (int j=0; j<raw.length; j++) {
                    //double xj = 2*normRaw[j]-normRaw[j-1];
                    double xj = raw[j];
                    double d = Math.abs(xi-xj);
                    ndc[i] += Math.exp(-alpha*d*d);
                }
            }

            // Max-Min normalize neighborhood density
            double ndcMax = Double.MIN_VALUE;
            double ndcMin = Double.MAX_VALUE;
            for (int i=0; i<ndc.length; i++) {
                if (ndcMax < ndc[i]) ndcMax = ndc[i];
                if (ndcMin > ndc[i]) ndcMin = ndc[i];
            }
            double ndcRange = ndcMax - ndcMin;
            for (int i=0; i<ndc.length; i++) {
                ndc[i] = (ndc[i] - ndcMin) / ndcRange;
            }

            //Discretize the neighborhood density curve
            int dSize = 100;
            double delta = rawRange / dSize;

            double dndcX[] = new double[dSize];
            double dndcXN[] = new double[dSize];
            double dndcY[] = new double[dSize];
            int dndcC[] = new int[dSize];
            for (int i=0; i<dSize; i++) {
                dndcX[i] = rawMin + delta/2 + i*delta;
                dndcXN[i] = (1/(double)dSize)/2 + (double)i/(double)dSize;
                dndcY[i] = 0;
                dndcC[i] = 0;
            }
            for (int i=0; i<ndc.length; i++) {
                int dcount = (int)((raw[i] - rawMin) / delta);
                if (dcount >= dSize) dcount = dSize-1;
                dndcY[dcount] += ndc[i];
                dndcC[dcount]++;
            }
            for (int i=0; i<dndcX.length; i++) {
                if (dndcC[i] > 0) {
                    dndcY[i] /= dndcC[i];
                }
            }

            /*------ Linearize the discrete neighborhood density curve ------ */
            TList vx = new TList();
            TList vy = new TList();
            TList dy = new TList();

            //Eliminate no-data points
            int i1 = 0;
            while (dndcC[i1] <= 0) i1++;
            vx.enList(Double.valueOf(dndcX[i1]));
            vy.enList(Double.valueOf(dndcY[i1]));
            dy.enList(Double.valueOf(0));
            for (int i2=i1+1; i2<dndcX.length; i2++) if (dndcC[i2] > 0) {
                vx.enList(Double.valueOf(dndcX[i2]));
                vy.enList(Double.valueOf(dndcY[i2]));
                double d = (dndcY[i2] - dndcY[i1]) / (dndcXN[i2] - dndcXN[i1]);
                dy.enList(Double.valueOf(d));
                i1 = i2;
            }

            //Linearize the has-data points
            FuzzyRule rule = new FuzzyRule();
            rule.setrMax(rawMax);
            rule.setrMin(rawMin);
            rule.add((Double)vx.get(0), (Double)vy.get(0));
            if (dy.len() > 1) {
                double thSlope = 1; // PI/4
                double slope1 = ((Double)dy.get(1)).doubleValue();
                for (int i=2; i<dy.len(); i++) {
                    double slope2 = ((Double)dy.get(i)).doubleValue();
                    double dSlope = Math.abs(slope2-slope1);
                    if (dSlope >= thSlope) {
                        rule.add((Double)vx.get(i-1), (Double)vy.get(i-1));
                        slope1 = slope2;
                    }
                }
                rule.add((Double)vx.get(vx.len()-1), (Double)vy.get(vy.len()-1));
            }

            return rule;
        }
    }

    //Handles the registration process for new nodes in a new thread
    private class NodeAnnotation extends CoapClientApplication implements Runnable{
        private String sensorMACAddr;
        private InetAddress sensorAddr;
        private Object monitor = new Object();
        private CoapResponse coapResponse;

        private String part1 = "http://uberdust.cti.gr/rest/testbed/1/node/urn:wisebed:ctitestbed:";
        private String part2 = "/capability/urn:wisebed:node:capability:light/tabdelimited/limit/"; //Light is used
        //private String part2 = "/capability/urn:wisebed:node:capability:temperature/tabdelimited/limit/";
        private int numberOfSamples;
        private TList DataStorage;

        public NodeAnnotation(InetAddress sensorAddr){
            super();
            numberOfAnnotationDemo++;
            if (CoapNodeRegistrationServer.numberOfAnnotationDemo % 2 == 1)
                sensorMACAddr = "0x132";
            else
                sensorMACAddr = "0x122";
            log.debug("[CoapNodeRegistrationServer] SensorID = " + sensorMACAddr +"; current number of annotation ="+
            numberOfAnnotationDemo);

            this.sensorAddr = sensorAddr;

            //Add sensors in 4 rooms to the list of known sensors
            DataStorage = new TList();

            //Room 0.I.3
            DataStorage.enList(new DataEntry("0x786a", "Room-0.I.3"));
            DataStorage.enList(new DataEntry("0x132", "Room-0.I.3"));

            //Room 0.I.2
            DataStorage.enList(new DataEntry("0x122", "Room-0.I.2"));
            DataStorage.enList(new DataEntry("0x1b77", "Room-0.I.2"));

            //Number of samples to be pulled down from Uberdust
            numberOfSamples = 1000;
        }

        private double calculateScore(List<Double> dataList, FuzzyRule rule, FuzzyRule ruleD) {
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

            double sc = 0;
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
            return sc;
        }

        private CoapRequest makeCOAPRequest(String remoteIP, String resultAnnotation) throws URISyntaxException, ToManyOptionsException, InvalidOptionException, InvalidMessageException, MessageDoesNotAllowPayloadException {
            URI AnnotationServiceURI = new URI("coap://" + remoteIP + ":5683/rdf");
            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.POST, AnnotationServiceURI);
            coapRequest.setContentType(OptionRegistry.MediaType.APP_N3);
            String payloadStr = "\0<coap://"+remoteIP+"/rdf>\0" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\0" +
                    "<http://spitfire-project.eu/foi/"+resultAnnotation+">\0";
            byte[] payload = payloadStr.getBytes(Charset.forName("UTF-8"));
            coapRequest.setPayload(payload);

            return coapRequest;
        }

        @Override
        public void run(){

            //Pull recorded sensor data from Uberdust,
            //note that one of the sensor in this list will be used as "new sensor" at demo
            for (int i=0; i<DataStorage.len(); i++) {
                DataEntry de = (DataEntry)DataStorage.get(i);
                try{
                    URL uberdust = new URL(part1+de.getMACAddr()+part2+String.valueOf(numberOfSamples));
                    try {
                        System.out.print("Pulling data for sensor "+de.getMACAddr()+" which locates in "+de.getSD()+"...");
                        URLConnection connection = uberdust.openConnection();
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        TString st = new TString('\t');
                        String line;
                        while ((line = in.readLine()) != null) {
                            st.setStr(line);
                            Long ts = Long.valueOf(st.getStrAt(0));
                            Double vl = Double.valueOf(st.getStrAt(1));
                            de.add(ts, vl);
                            //log.debug(String.format(Locale.GERMANY, "%d %.4f", ts, vl));
                        }
                        log.debug(" Done!");
                    } catch (IOException ioe) {
                        log.debug("Cannot open HTTP connection to Uberdust server!");
                    }
                }catch (MalformedURLException e) {
                    log.debug("Malformed URL!");
                }
            }

            //Index of the new sensor in data storage
            int newSensorIND = -1;

            //Calculate fuzzy set of sensors
            for (int i=0; i<DataStorage.len(); i++) {
                DataEntry de = (DataEntry)DataStorage.get(i);
                //Only compute fuzzy set of already annotated sensors
                if (!de.getMACAddr().equalsIgnoreCase(sensorMACAddr)) {
                    System.out.print("Computing fuzzy set for sensor "+de.getMACAddr()+"... ");
                    de.computeFuzzySet();
                    log.debug(" Done!");
                } else {
                    newSensorIND = i;
                }
            }

            //Search for annotation
            log.debug("Search for annotation... ");
            DataEntry newSensorEntry = (DataEntry)DataStorage.get(newSensorIND);
            double maxsc = 0;
            String resultAnnotation = null;
            for (int i=0; i<DataStorage.len(); i++) {
                DataEntry de = (DataEntry)DataStorage.get(i);
                if (!de.getMACAddr().equalsIgnoreCase(sensorMACAddr)) {
                    double sc = calculateScore(newSensorEntry.getValues(), de.getFZ(), de.getDFZ());
                    if (maxsc < sc) {
                        maxsc = sc;
                        resultAnnotation = de.getSD();
                    }
                    log.debug("Similarity to "+de.getMACAddr()+" in "+de.getSD()+" is "+String.format(Locale.GERMANY, "%.10f", sc));
                }
            }
            log.debug("Resulting annotation is "+resultAnnotation);


            //Send the annotation back to the new sensor


            //String resultAnnotation = "TestRoom01";
            String remoteIP = sensorAddr.getHostAddress();
            if(remoteIP.indexOf("%") != -1){
                remoteIP = remoteIP.substring(0, remoteIP.indexOf("%"));
            }
            if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
                remoteIP = "[" + remoteIP + "]";
            }
            try {
                CoapRequest annotation = makeCOAPRequest(remoteIP, resultAnnotation);
                log.debug("Sending POST request to sensor!");
                writeCoapRequest(annotation);
            } catch (URISyntaxException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (ToManyOptionsException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (InvalidOptionException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (InvalidMessageException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (MessageDoesNotAllowPayloadException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }

        /*@Override
        public void receiveResponse(CoapResponse coapResponse) {
            log.debug("Received response for annotation service");
            synchronized (monitor){
                this.coapResponse = coapResponse;
                coapResponse.getCode();
                monitor.notify();
            }
        } */
    }

    /* -------------------------------- END OF SENSOR ANNOTATION IMPLEMENTATION ---------------------------------- */
}
