package eu.spitfire.ssp.server.channels.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 11.10.13
 * Time: 17:34
 * To change this template use File | Settings | File Templates.
 */
public class DummyHandler extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me){
        log.info("Message received from {}", me.getRemoteAddress());
        ctx.sendUpstream(me);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        log.info("Message to be sent to {}", me.getRemoteAddress());
        ctx.sendDownstream(me);
    }

}
