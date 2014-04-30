package eu.spitfire.ssp.server.http.webservices;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

/**
 * Abstract class to be extended for non-semantic HTTP Webservices (i.e. no proxying Webservices) offered by the
 * Smart Service Proxy. Usually there is no need to use this class, at all. However, its purpose is rather internal
 * usage.
 *
 * For semantic (i.e. proxying) Webservices extend
 * {@link HttpSemanticProxyWebservice}!
 *
 * @author Oliver Kleine
 */
public abstract class HttpWebservice extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    protected ExecutorService ioExecutorService;


    public void setIoExecutorService(ExecutorService executorService){
        this.ioExecutorService = executorService;
    }


    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me){
        if(me.getMessage() instanceof HttpRequest)
            processHttpRequest(ctx.getChannel(), (HttpRequest) me.getMessage(), (InetSocketAddress) me.getRemoteAddress());

        else
            ctx.sendUpstream(me);
    }


    public abstract void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress);


    protected void writeHttpResponse(Channel channel, HttpResponse httpResponse, final InetSocketAddress clientAddress){
        log.info("Write Response!");
        ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
        future.addListener(ChannelFutureListener.CLOSE);

        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {

                if(future.isSuccess())
                    log.debug("Succesfully written HTTP response to {}", clientAddress);
                else
                    log.error("Could not send HTTP response to {}!", clientAddress, future.getCause());

            }
        });
    }
}
