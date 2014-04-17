package eu.spitfire.ssp.server.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link org.jboss.netty.channel.ChannelHandler} is basically supposed to be used for information or debugging
 * purposes regarding the source of incoming messages and the destination of outgoing messages.
 *
 * @author Oliver Kleine
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
