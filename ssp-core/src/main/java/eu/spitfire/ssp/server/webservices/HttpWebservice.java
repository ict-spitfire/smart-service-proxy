package eu.spitfire.ssp.server.webservices;

import eu.spitfire.ssp.server.internal.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstract class to be extended for non-semantic HTTP Webservices (i.e. no proxying Webservices) offered by the
 * Smart Service Proxy. Usually there is no need to use this class, at all. However, its purpose is rather internal
 * usage.
 *
 * For semantic (i.e. proxying) Webservices extend
 * {@link eu.spitfire.ssp.backend.generic.DataOriginMapper}!
 *
 * @author Oliver Kleine
 */
public abstract class HttpWebservice extends SimpleChannelHandler {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ExecutorService ioExecutor;
    private ScheduledExecutorService internalTasksExecutor;
    private String htmlResourcePath;


    public HttpWebservice(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
                          String htmlResourcePath){

        this.ioExecutor = ioExecutor;
        this.internalTasksExecutor = internalTasksExecutor;
        this.htmlResourcePath = htmlResourcePath;
    }

    @Override
    public final void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me){
        if(me.getMessage() instanceof HttpRequest){
            this.internalTasksExecutor.execute(new Runnable() {
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


    protected void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        try{
            if(httpRequest.getMethod() == HttpMethod.GET){
                processGet(channel, httpRequest, clientAddress);
            }

            else if(httpRequest.getMethod() == HttpMethod.POST){
                processPost(channel, httpRequest, clientAddress);
            }

            else if(httpRequest.getMethod() == HttpMethod.PUT){
                processPut(channel, httpRequest, clientAddress);
            }

            else if(httpRequest.getMethod() == HttpMethod.DELETE){
                processDelete(channel, httpRequest, clientAddress);
            }

            else{
                sendMethodNotAllowed(
                        httpRequest.getMethod().getName(), channel, httpRequest.getProtocolVersion(), clientAddress
                );
            }
        }

        catch(Exception ex){
            log.error("Internal Server Error because of exception!", ex);
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }


    protected void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        if(this.htmlResourcePath == null){
            sendMethodNotAllowed("GET", channel, httpRequest.getProtocolVersion(), clientAddress);
            return;
        }

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK,
                this.getHtmlContent(),
                "text/html"
        );

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    protected void processPost(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        sendMethodNotAllowed("POST", channel, httpRequest.getProtocolVersion(), clientAddress);
    }


    protected void processPut(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

       sendMethodNotAllowed("PUT", channel, httpRequest.getProtocolVersion(), clientAddress);
    }


    protected void processDelete(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception{

        sendMethodNotAllowed("DELETE", channel, httpRequest.getProtocolVersion(), clientAddress);
    }


    private void sendMethodNotAllowed(String methodName, Channel channel, HttpVersion httpVersion,
                                      InetSocketAddress clientAddress) throws Exception{

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                httpVersion, HttpResponseStatus.METHOD_NOT_ALLOWED, "HTTP " + methodName + " is not allowed!"
        );

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private ChannelBuffer getHtmlContent() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(this.htmlResourcePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder content = new StringBuilder();
        String line = reader.readLine();
        while(line != null){
            content.append(line);
            content.append("\n");
            line = reader.readLine();
        }

        return ChannelBuffers.wrappedBuffer(content.toString().getBytes(Charset.forName("UTF-8")));
    }


    protected void writeHttpResponse(final Channel channel, final HttpResponse httpResponse,
                                     final InetSocketAddress clientAddress){

        this.ioExecutor.execute(new Runnable(){

            @Override
            public void run() {
                log.info("Write Response!");
                ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
                future.addListener(ChannelFutureListener.CLOSE);

                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {

                        if(future.isSuccess())
                            log.debug("Successfully written HTTP response to {}", clientAddress);
                        else
                            log.error("Could not send HTTP response to {}!", clientAddress, future.getCause());

                    }
                });
            }
        });
    }

    protected ExecutorService getIoExecutor(){
        return this.ioExecutor;
    }

    protected ScheduledExecutorService getInternalExecutor(){
        return this.internalTasksExecutor;
    }
}
