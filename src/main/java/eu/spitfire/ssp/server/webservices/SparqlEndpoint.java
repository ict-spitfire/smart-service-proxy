package eu.spitfire.ssp.server.webservices;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.messages.InternalSparqlQueryMessage;
import eu.spitfire.ssp.server.channels.handler.cache.SemanticCache;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.09.13
 * Time: 10:00
 * To change this template use File | Settings | File Templates.
 */
public class SparqlEndpoint extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private LocalServerChannel localChannel;
    private ExecutorService executorService;
    private AtomicInteger openQueriesCount;

    public SparqlEndpoint(LocalServerChannel localChannel, ExecutorService executorService) {
        this.localChannel = localChannel;
        this.executorService = executorService;
        this.openQueriesCount = new AtomicInteger(0);
    }

//    private synchronized void increaseCounter(){
//        counter++;
//        log.info("Start new SPARQL query. Now running: {}", counter);
//    }
//
//    private synchronized void decreaseCounter(){
//        counter--;
//        log.info("Finished SPARQL query. Now running: {}", counter);
//    }


    @Override
    public void processHttpRequest(final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress) {

        final HttpVersion httpVersion = httpRequest.getProtocolVersion();

        if(httpRequest.getMethod() != HttpMethod.POST){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                    HttpResponseStatus.METHOD_NOT_ALLOWED, "Only POST is supported.");

            writeHttpResponse(channel, httpResponse, clientAddress);
        }

        else{

            String query = httpRequest.getContent().toString(Charset.forName("UTF-8"));
            final SettableFuture<String> resultFuture = SettableFuture.create();
            InternalSparqlQueryMessage queryMessage = new InternalSparqlQueryMessage(query, resultFuture);

            ChannelFuture future = Channels.write(localChannel, queryMessage);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess())
                        log.info("New SPARQL query started (Now running: {}).", openQueriesCount.incrementAndGet());
                    else
                        resultFuture.setException(future.getCause());
                }
            });

            Futures.addCallback(resultFuture, new FutureCallback<String>() {
                @Override
                public void onSuccess(String queryResult) {
                    log.info("SPARQL query result created (Now running: {}).", openQueriesCount.decrementAndGet());

                    byte[] payloadBytes = queryResult.getBytes(Charset.forName("UTF-8"));
                    ChannelBuffer payload = ChannelBuffers.wrappedBuffer(payloadBytes);

                    Multimap<String, String> header = HashMultimap.create();
                    header.put(HttpHeaders.Names.CONTENT_LENGTH, "" + payload.readableBytes());

                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.OK, header, payload);
                    writeHttpResponse(channel, httpResponse, clientAddress);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.info("Could not create SPARQL query result (Now running: {}).",
                            openQueriesCount.decrementAndGet());

                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());
                    writeHttpResponse(channel, httpResponse, clientAddress);
                }

            }, ioExecutorService);
        }
    }
}
