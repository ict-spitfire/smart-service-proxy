package eu.spitfire.ssp.backends.slse.webservice;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.graph.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import de.uniluebeck.itm.xsd.slse.jaxb.SemanticEntity;
import de.uniluebeck.itm.xsd.slse.tools.SemanticEntityUnmarshaller;
import eu.spitfire.ssp.backends.slse.SlseBackendComponentFactory;
import eu.spitfire.ssp.backends.slse.SlseDataOrigin;
import eu.spitfire.ssp.backends.slse.SlseRegistry;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by olli on 19.06.14.
 */
public class HttpVirtualSensorCreatedWebservice extends HttpWebservice{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private SlseBackendComponentFactory componentFactory;
    private SlseRegistry slseRegistry;

    public HttpVirtualSensorCreatedWebservice(SlseBackendComponentFactory componentFactory){
        this.componentFactory = componentFactory;
        this.slseRegistry = (SlseRegistry) componentFactory.getDataOriginRegistry();
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) throws Exception {
        try{
            if(httpRequest.getMethod() == HttpMethod.POST){
                processPost(channel, httpRequest, clientAddress);
            }

            else{
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED, "Method " + httpRequest.getMethod() + " not allowed!");

                writeHttpResponse(channel, httpResponse, clientAddress);
            }
        }
        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private void processPost(final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress) {
        final SettableFuture<HttpResponse> httpResponseFuture = SettableFuture.create();
        Futures.addCallback(httpResponseFuture, new FutureCallback<HttpResponse>() {

            @Override
            public void onSuccess(HttpResponse httpResponse) {
                writeHttpResponse(channel, httpResponse, clientAddress);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.internalTasksExecutorService);


        try{

            ChannelBufferInputStream inputStream = new ChannelBufferInputStream(httpRequest.getContent());
            String line = inputStream.readLine();
            log.warn("Line: {}", line);
            while(!line.startsWith("<?xml")){
                try{
                    line = inputStream.readLine();
                    log.warn("Line: {}", line);
                }
                catch(IndexOutOfBoundsException ex){

                }

            }

            final List<String> registeredEntities = new ArrayList<>();
            List<SemanticEntity> semanticEntities = SemanticEntityUnmarshaller.unmarshal(inputStream).getEntities();
            List<ListenableFuture<Void>> registrationFutures = new ArrayList<>();

            for(SemanticEntity semanticEntity : semanticEntities){
                final URI graphName = new URI("http", null, this.componentFactory.getSspHostName(), -1,
                        "/" + semanticEntity.getUriPath().getValue(), null, null);

                String sparqlQuery = semanticEntity.getSparqlQuery().getValue();
                SlseDataOrigin dataOrigin = new SlseDataOrigin(
                        graphName, QueryFactory.create(sparqlQuery)
                );

                ListenableFuture<Void> registrationFuture = this.slseRegistry.registerSlseDataOrigin(dataOrigin);

                Futures.addCallback(registrationFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void result) {
                        registeredEntities.add(graphName.toString());
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Could not register SLSE {}", graphName, t);
                    }
                });

                registrationFutures.add(registrationFuture);
            }

            Futures.addCallback(Futures.allAsList(registrationFutures), new FutureCallback<List<Void>>() {
                @Override
                public void onSuccess(@Nullable List<Void> result) {
                    sendResult();
                }

                @Override
                public void onFailure(Throwable t) {
                    sendResult();
                }

                public void sendResult(){
                    StringBuilder contentBuilder = new StringBuilder();
                    contentBuilder.append("Newly registered Semantic Entities: \n");

                    for(String graphName : registeredEntities){
                        contentBuilder.append(graphName).append("\n");
                    }

                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                                HttpResponseStatus.OK, contentBuilder.toString());

                    httpResponseFuture.set(httpResponse);
                }
            });

        }

        catch (Exception ex){
            log.error("Exception during SLSE batch creation!", ex);
            httpResponseFuture.setException(ex);
        }

    }
}
