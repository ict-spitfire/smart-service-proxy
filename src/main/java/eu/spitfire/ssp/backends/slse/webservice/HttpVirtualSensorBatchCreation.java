package eu.spitfire.ssp.backends.slse.webservice;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.hp.hpl.jena.query.QueryFactory;
import de.uniluebeck.itm.xsd.slse.jaxb.SemanticEntity;
import de.uniluebeck.itm.xsd.slse.tools.SemanticEntityUnmarshaller;
import eu.spitfire.ssp.backends.slse.SlseBackendComponentFactory;
import eu.spitfire.ssp.backends.slse.SlseDataOrigin;
import eu.spitfire.ssp.backends.slse.SlseRegistry;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.multipart.DiskFileUpload;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jboss.netty.handler.codec.http.multipart.MixedFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by olli on 25.06.14.
 */
public class HttpVirtualSensorBatchCreation extends HttpWebservice{

    private Logger log = LoggerFactory.getLogger(HttpVirtualSensorBatchCreation.class.getName());

    private String graphNamePrefix;
    private SlseRegistry slseRegistry;


//    private AtomicInteger ongoingRegistrations = new AtomicInteger(0);

    public HttpVirtualSensorBatchCreation(SlseBackendComponentFactory componentFactory){
        this.graphNamePrefix = "http://" + componentFactory.getSspHostName() + "/virtual-sensor";
        this.slseRegistry = (SlseRegistry) componentFactory.getDataOriginRegistry();
    }


    @Override
    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception {
        try{
            if(httpRequest.getMethod() == HttpMethod.GET){
                processGet(channel, httpRequest, clientAddress);
            }

            else if(httpRequest.getMethod() == HttpMethod.POST){
                processPost(channel, httpRequest, clientAddress);
            }

            else{
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED, "Method " + httpRequest.getMethod() + " not allowed!");

                writeHttpResponse(channel, httpResponse, clientAddress);
            }
        }
        catch(Exception ex){
            log.error("Internal Server Error because of exception!", ex);
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());

            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }


    private void processGet(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress)
            throws Exception {

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, getHtmlContent(), "text/html");

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private void processPost(final Channel channel, final HttpRequest httpRequest, final InetSocketAddress clientAddress)
            throws Exception {

        InputStream xmlInputStream = getXMLInputStream(httpRequest);
        List<SemanticEntity> semanticEntities = SemanticEntityUnmarshaller.unmarshal(xmlInputStream).getEntities();

        Map<URI, ListenableFuture<Void>> registrationFutures = new LinkedHashMap<>(semanticEntities.size());
        final List<URI> registeredGraphNames = new ArrayList<>();

        for(SemanticEntity semanticEntity : semanticEntities){
            URI graphName = new URI(graphNamePrefix + semanticEntity.getUriPath().getValue());


            registrationFutures.put(graphName, this.register(semanticEntity));
        }

        final AtomicInteger counter = new AtomicInteger(0);

        for(final URI graphName : registrationFutures.keySet()){
            Futures.addCallback(registrationFutures.get(graphName), new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    log.info("Registration count: {}", counter.incrementAndGet());
                    log.info("Successfully registered virtual sensor with graph name : {}", graphName);
                    registeredGraphNames.add(graphName);
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Could not register virtual sensor with graph name: {}", graphName, t);
                }
            }, this.internalTasksExecutorService);
        }

        Futures.addCallback(Futures.allAsList(registrationFutures.values()), new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(@Nullable List<Void> result) {
                sendResult();
            }

            @Override
            public void onFailure(Throwable t) {
                sendResult();
            }

            private void sendResult(){
                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Newly registered Semantic Entities: \n");

                for(URI graphName : registeredGraphNames){
                    contentBuilder.append(graphName).append("\n");
                }

                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.OK, contentBuilder.toString());

                writeHttpResponse(channel, httpResponse, clientAddress);
            }

        }, this.internalTasksExecutorService);
    }


    private InputStream getXMLInputStream(HttpRequest httpRequest) throws Exception{
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);

        while(decoder.hasNext()){
            InterfaceHttpData httpData = decoder.next();
            if(httpData.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload){
                if(httpData instanceof MixedFileUpload){
                    MixedFileUpload mixedFileUpload = (MixedFileUpload) httpData;
                    return new ByteArrayInputStream(mixedFileUpload.get());
                }
            }
        }

        return null;
    }


    private ListenableFuture<Void> register(SemanticEntity semanticEntity) throws Exception{

        final URI graphName = new URI(graphNamePrefix + semanticEntity.getUriPath().getValue());
        String sparqlQuery = semanticEntity.getSparqlQuery().getValue();

        SlseDataOrigin dataOrigin = new SlseDataOrigin(
                graphName, QueryFactory.create(sparqlQuery)
        );

        return this.slseRegistry.registerSlseDataOrigin(dataOrigin);
    }


    private ChannelBuffer getHtmlContent() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("html/VirtualSensorBatchCreationSite.html");
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
}
