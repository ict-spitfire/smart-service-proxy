package eu.spitfire.ssp.server.http.webservices;

import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

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
    protected ScheduledExecutorService internalTasksExecutorService;

    public void setIoExecutorService(ExecutorService executorService){
        this.ioExecutorService = executorService;
    }

    public void setInternalTasksExecutorService(ScheduledExecutorService internalTasksExecutorService){
        this.internalTasksExecutorService = internalTasksExecutorService;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me){
        if(me.getMessage() instanceof HttpRequest){
            this.internalTasksExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try{
                        processHttpRequest(ctx.getChannel(), (HttpRequest) me.getMessage(),
                                (InetSocketAddress) me.getRemoteAddress());
                    }
                    catch(Exception ex){
                        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                                ((HttpRequest) me.getMessage()).getProtocolVersion(),
                                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                ex.getMessage()
                        );

                        writeHttpResponse(ctx.getChannel(), httpResponse, (InetSocketAddress) me.getRemoteAddress());
                    }
                }
            });
        }

        else{
            ctx.sendUpstream(me);
        }
    }


    public abstract void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) throws Exception;


    protected void writeHttpResponse(final Channel channel, final HttpResponse httpResponse,
                                     final InetSocketAddress clientAddress){

        this.ioExecutorService.execute(new Runnable(){

            @Override
            public void run() {
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
        });
    }
}
