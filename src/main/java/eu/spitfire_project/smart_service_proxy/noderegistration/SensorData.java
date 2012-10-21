package eu.spitfire_project.smart_service_proxy.noderegistration;

import com.google.common.collect.Iterables;
import eu.spitfire_project.smart_service_proxy.triplestore.SpitfireHandler;
import eu.spitfire_project.smart_service_proxy.utils.TList;
import eu.spitfire_project.smart_service_proxy.utils.TString;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;

public class SensorData {
    private Logger log = Logger.getLogger(SensorData.class.getName());
    public String ipv6Addr = null;
    public String macAddr = null;
    public String FOI = null;
    public String httpRequestUri = null;
    private static int ID = 0; //Sensor ID

    private int MAX_NUMBER_OF_VALUES = 96;
    private LinkedHashMap<Long, Double> readings = new LinkedHashMap<Long, Double>(MAX_NUMBER_OF_VALUES + 1, .75F, false){
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            boolean result = size() > MAX_NUMBER_OF_VALUES;
            log.debug("Remove eldest: " + result);
            return result;
        }
    };

    //private ArrayList<Long> timeStamps = new ArrayList<Long>(); //Time-stamp of a samples
    //private ArrayList<Double> values = new ArrayList<Double>(); //Value of the samples
    public long annoTimer;
    private Random random = new Random();
    private FuzzyRule fz, dfz;
    public int senID;
    public SensorData(String ipv6Addr, String macAddr, String httpRequestUri, String FOI) {
        senID = ID;
        ID++;
        this.ipv6Addr = ipv6Addr;
        this.macAddr = macAddr;
        this.httpRequestUri = httpRequestUri;
        this.FOI = FOI;

        if ("Unannotated".equalsIgnoreCase(FOI))
            annoTimer = System.currentTimeMillis();
    }

    private void updateReadings(long timeStamp, double value) {
        log.debug("Before: " + readings.keySet().size());
        readings.put(timeStamp, value);
        log.debug("After: " + readings.keySet().size());
//        timeStamps.add(timeStamp);
//        if (timeStamps.size() >= numberOfImagesPerDay)
//            timeStamps.remove(0);
//        values.add(value);
//        if (values.size() >= numberOfImagesPerDay)
//            values.remove(0);
    }

    public List<Double> getValues() {
        return new ArrayList<Double>(readings.values());
    }

    public long getLatestTS() {
        log.debug("Number of readings: " + readings.keySet().size());
        if(!readings.isEmpty()){
            return Iterables.getLast(readings.keySet());
        }
        else{
            return 0;
        }
//        long rs = 0;
//        if (timeStamps.size() > 0)
//            rs = timeStamps.get(timeStamps.size()-1);
//        return rs;
    }

    public double getLatestVL() {
        if(!readings.isEmpty()){
            return Iterables.getLast(readings.values());
        }
        else{
            return 0;
        }
//        double rs = 0;
//        if (values.size() > 0)
//            rs = values.get(values.size()-1);
//        return rs;
    }

    public void computeFuzzySet(int nDataPoint) {
        log.debug("Start computing FZ");
        ArrayList<Double> readingValues = new ArrayList<Double>(readings.values());
        fz = extractRule(readingValues, nDataPoint);
        dfz = extractRuleD(readingValues, nDataPoint);
//        fz = extractRule(values, nDataPoint);
//        dfz = extractRuleD(values, nDataPoint);
    }

    public FuzzyRule getFZ() {
        return fz;
    }

    public FuzzyRule getDFZ() {
        return dfz;
    }

    private FuzzyRule extractRuleD(List<Double> datList, int nDataPoint) {
        List<Double> dataList = new ArrayList<Double>();
        for (int i=0; i<nDataPoint; i++)
            dataList.add(datList.get(i));

        FuzzyRule ruleD = null;

        if (dataList.size() > 2) {
            ArrayList<Double> deriList = new ArrayList<Double>();
            Double[] raw = dataList.toArray(new Double[dataList.size()]);
            for (int i=0; i<raw.length-1; i++) {
                Double tmp = raw[i + 1] - raw[i];
                deriList.add(tmp);
            }
            deriList.add(deriList.get(deriList.size()-1));
            ruleD = extractRule(deriList, nDataPoint);
        }

        return ruleD;
    }

    private FuzzyRule extractRule(List<Double> datList, int nDataPoint) {
        log.debug("Start extractRule");
        List<Double> dataList = new ArrayList<Double>();
        for (int i=0; i<nDataPoint; i++)
            dataList.add(datList.get(i));

        Double[] raw = dataList.toArray(new Double[dataList.size()]);

        // Identify the value range
        double rawMax = Collections.max(dataList);
        double rawMin = Collections.min(dataList);
        double rawRange = rawMax - rawMin;

        FuzzyRule rule = null;

        // Special case: all values are the same
        /*if (rawRange <= Double.MIN_VALUE) {
          log.debug("All values in snapshot are identical");
          double epsilon = 0.05;
          rule.setrMax(rawMax + epsilon);
          rule.setrMin(rawMin - epsilon);
          rule.add(raw[0] - epsilon, 1);
          rule.add(raw[0] + epsilon, 1);
      } else {*/
        if (rawRange > Double.MIN_VALUE) {
            rule = new FuzzyRule();
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
            log.debug("Returning a rule 0");
            /*------ Linearize the discrete neighborhood density curve ------ */
            TList vx = new TList();
            TList vy = new TList();
            TList dy = new TList();

            //Eliminate no-data points
            int i1 = 0;
            while (dndcC[i1] <= 0) i1++;
            vx.enList(dndcX[i1]);
            vy.enList(dndcY[i1]);
            dy.enList((double) 0);
            for (int i2=i1+1; i2<dndcX.length; i2++) if (dndcC[i2] > 0) {
                vx.enList(dndcX[i2]);
                vy.enList(dndcY[i2]);
                double d = (dndcY[i2] - dndcY[i1]) / (dndcXN[i2] - dndcXN[i1]);
                dy.enList(d);
                i1 = i2;
            }

            //Linearize the has-data points
            rule.setrMax(rawMax);
            rule.setrMin(rawMin);
            rule.add((Double)vx.get(0), (Double)vy.get(0));
            if (dy.len() > 1) {
                double thSlope = 1; // PI/4
                double slope1 = (Double) dy.get(1);
                for (int i=2; i<dy.len(); i++) {
                    double slope2 = (Double) dy.get(i);
                    double dSlope = Math.abs(slope2-slope1);
                    if (dSlope >= thSlope) {
                        rule.add((Double)vx.get(i-1), (Double)vy.get(i-1));
                        slope1 = slope2;
                    }
                }
                rule.add((Double)vx.get(vx.len()-1), (Double)vy.get(vy.len()-1));
            }
        }

        log.debug("Returning a rule");
        return rule;
    }



    public void crawl() {

        try {

            long currentTime = System.currentTimeMillis();
            //updateReadings(currentTime, random.nextInt(1000));
            log.debug("Crawl for " + macAddr + " at time " + currentTime + "...");
            URL crawlRequest = new URL(httpRequestUri);
            URLConnection connection = crawlRequest.openConnection();

            //log.debug("connection opened (timeout: " + connection.getConnectTimeout() + "), ");

//            StringWriter payloadWriter = new StringWriter();
//            IOUtils.copy(connection.getInputStream(), payloadWriter, "UTF-8");
            String payload = "";

            InputStreamReader inputStreamReader = new  InputStreamReader(connection.getInputStream());
            BufferedReader in = new BufferedReader(inputStreamReader);

            String line;

            double value = 0;
            while ((line = in.readLine()) != null) {
                log.debug("content: " + line + ", ");
                if (line.indexOf("value")>0) {
                    //log.debug("The value line is: "+line);
                    TString s1 = new TString(line, '>');
                    TString s2 = new TString(s1.getStrAt(1),'<');
                    //log.debug("new value crawled is: "+s2.getStrAt(0));
                    value = Double.valueOf(s2.getStrAt(0));
                }
                payload += line;
            }
            in.close();

            updateReadings(currentTime, value);

            URI uri = new URI(httpRequestUri);
            SpitfireHandler spitfireHandler = new SpitfireHandler(uri, payload);
            spitfireHandler.run();
            //updateReadings(currentTime, random.nextInt(1000));

            log.debug(" Done for " + macAddr + " with value:" + String.format("%.2f", value));
        } catch (MalformedURLException e) {
            log.debug("failed to crawl for "+macAddr, e);
        } catch (IOException e) {
            log.debug("failed to crawl for " + macAddr, e);
        } catch (URISyntaxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        /*URL crawRequest = null;
        try {
            log.debug("crawl for " + macAddr + " at time " + simTime + "...");
            crawRequest = new URL(httpRequestUri);
            URLConnection connection = crawRequest.openConnection();

            log.debug("connection opened (timeout: " + connection.getConnectTimeout() + "), ");
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = in.readLine();
            log.debug("content: " + line + ", ");
            double value = 0;
            while ((line = in.readLine()) != null) {
                log.debug("content: " + line + ", ");
                if (line.indexOf("value")>0) {
                    //log.debug("The value line is: "+line);
                    TString s1 = new TString(line, '>');
                    TString s2 = new TString(s1.getStrAt(1),'<');
                    //log.debug("new value crawled is: "+s2.getStrAt(0));
                    value = Double.valueOf(s2.getStrAt(0)).doubleValue();
                }
                log.debug("doing stuff, ");
            }
            in.close();


            updateReadings(simTime, value);
            log.debug(" Done for " + macAddr + " with value:" + String.format("%.2f", value));
        } catch (MalformedURLException e) {
            log.debug("failed to crawl for "+macAddr);
        } catch (IOException e) {
            log.debug("failed to crawl for " + macAddr);
        }*/
        //new HttpCrawler(this, macAddr, httpRequestUri).run();
    }

}
