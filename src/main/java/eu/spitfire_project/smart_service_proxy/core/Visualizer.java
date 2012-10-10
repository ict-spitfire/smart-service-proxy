package eu.spitfire_project.smart_service_proxy.core;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.net.Inet6Address;
import java.nio.charset.Charset;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 10.10.12
 * Time: 11:22
 * To change this template use File | Settings | File Templates.
 */
public class Visualizer extends SimpleChannelUpstreamHandler{

    private Logger log = Logger.getLogger(Visualizer.class.getName());

    private static final Visualizer instance = new Visualizer();

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
