package eu.spitfire.ssp.server.webservices;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.channels.handler.cache.SemanticCache;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.09.13
 * Time: 10:00
 * To change this template use File | Settings | File Templates.
 */
public class SparqlEndpoint implements HttpNonSemanticWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ExecutorService executorService;
    private SemanticCache cache;
    private int counter = 0;

    public SparqlEndpoint(ExecutorService executorService, SemanticCache cache) {
        this.executorService = executorService;
        this.cache = cache;
    }

    private synchronized void increaseCounter(){
        counter++;
        log.info("Start new SPARQL query. Now running: {}", counter);
    }

    private synchronized void decreaseCounter(){
        counter--;
        log.info("Finished SPARQL query. Now running: {}", counter);
    }

    @Override
    public void processHttpRequest(final SettableFuture<HttpResponse> settableFuture, final HttpRequest httpRequest) {

        if(httpRequest.getMethod() != HttpMethod.POST){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.METHOD_NOT_ALLOWED, "Only POST is supported.");
            settableFuture.set(httpResponse);
            return;
        }

        String sparqlQuery = httpRequest.getContent().toString(Charset.forName("UTF-8"));
        final SettableFuture<String> queryResultFuture = SettableFuture.create();

        increaseCounter();
        cache.processSparqlQuery(queryResultFuture, sparqlQuery);

        queryResultFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    String queryResult = queryResultFuture.get();

                    ChannelBuffer payload =
                            ChannelBuffers.wrappedBuffer(queryResult.getBytes(Charset.forName("UTF-8")));
                    Multimap<String, String> header = HashMultimap.create();
                    header.put(HttpHeaders.Names.CONTENT_LENGTH, "" + payload.readableBytes());

                    HttpResponse httpResponse =
                            HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                    HttpResponseStatus.OK, header, payload);

                    settableFuture.set(httpResponse);

                }
                catch (Exception e) {
                    settableFuture.setException(e);
                }
                finally {
                    decreaseCounter();
                }
            }
        }, executorService);
    }
}
