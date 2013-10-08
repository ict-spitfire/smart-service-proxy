package eu.spitfire.ssp.server.channels.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The internal channels sink has no functionality but to discard messages written downstream on the
 * internal channel. If the logging level for this class is set to DEBUG, it will log a discard message. So it
 * is only existent for debugging purposes.
 *
 * @author Oliver Kleine
 */
public class InternalPipelineSink extends SimpleChannelDownstreamHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        me.getFuture().setSuccess();
        log.debug("Internal channels sink received downstream message to be discarded: {}", me.getMessage());
    }
}
