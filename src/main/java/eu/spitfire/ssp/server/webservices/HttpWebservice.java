package eu.spitfire.ssp.server.webservices;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;

/**
 * Abstract class to be extended for non-semantic HTTP Webservices (i.e. no proxying Webservices) offered by the
 * Smart Service Proxy. Usually there is no need to use this class, at all. However, its purpose is rather internal
 * usage.
 *
 * For semantic (i.e. proxying) Webservices extend
 * {@link eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice}!
 *
 * @author Oliver Kleine
 */
public abstract class HttpWebservice extends SimpleChannelUpstreamHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me){
        if(me.getMessage() instanceof HttpRequest)
            processHttpRequest(ctx.getChannel(), (HttpRequest) me.getMessage(), (InetSocketAddress) me.getRemoteAddress());

        else
            ctx.sendUpstream(me);
    }


    public abstract void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress);

}
