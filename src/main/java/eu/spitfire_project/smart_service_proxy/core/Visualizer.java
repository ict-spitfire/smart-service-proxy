package eu.spitfire_project.smart_service_proxy.core;

import eu.spitfire_project.smart_service_proxy.utils.TList;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.net.Inet6Address;
import java.nio.charset.Charset;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * Written: Cuong Truong
 * Date: 10.10.12
 * Time: 11:22
 * To change this template use File | Settings | File Templates.
 */
public class Visualizer extends SimpleChannelUpstreamHandler{
    private Logger log = Logger.getLogger(Visualizer.class.getName());
    private static final Visualizer instance = new Visualizer();

    private class Reading {
        public long time;
        public double value;
    }

    private class SensorData {
        public String ID = null;
        public String FOI = null;
        public TList readings = null;
        public double maxValue, minValue;
        public long timeL, timeR;
        public int nSamples, sampleRate;

        public SensorData(String ID, String FOI, int nSamples, int sampleRate) {
            this.ID = ID;
            this.FOI = FOI;
            this.nSamples = nSamples;
            this.sampleRate = sampleRate;
            this.readings = new TList(nSamples); //Maximum number of readings
            maxValue = Double.MIN_VALUE;
            minValue = Double.MAX_VALUE;
        }

        private void updateReadings(Reading r) {
            readings.enQueue(r);
            if (maxValue < r.value) maxValue = r.value;
            if (minValue > r.value) minValue = r.value;
            timeL = ((Reading)(readings.get(0))).time;
            timeR = ((Reading)(readings.get(readings.len()-1))).time;
        }

        public void crawl() {
            Reading data = new Reading();
            data.time = System.currentTimeMillis();
            updateReadings(data);
        }
    }

    private int nSamples = 288;
    private int sampleRate = 500;
    private TList sensors = new TList();
    private TList newsens = new TList();

    private Visualizer(){
    }

    public static Visualizer getInstance(){
        return instance;
    }

    public void addSensorNode(Inet6Address address){
        //Add it to the list
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me){
        log.debug("Message received!");
        if(!(me.getMessage() instanceof HttpRequest)){
            ctx.sendUpstream(me);
            return;
        }

        HttpRequest request = (HttpRequest) me.getMessage();

        //Do something with the request

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        String payload = "TEST 123";
        response.setContent(ChannelBuffers.copiedBuffer(payload.getBytes(Charset.forName("UTF-8"))));

        ChannelFuture future = Channels.write(ctx.getChannel(), response);
        future.addListener(ChannelFutureListener.CLOSE);
    }
}
