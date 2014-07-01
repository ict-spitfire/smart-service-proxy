package eu.spitfire.ssp.server.http.webservices;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import eu.spitfire.ssp.server.common.messages.SparqlQueryMessage;
import eu.spitfire.ssp.server.common.messages.SparqlQueryResultMessage;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.MixedAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

/**
 * Created by olli on 01.07.14.
 */
public class HttpSparqlEndpoint extends HttpWebservice{

    private Logger log = LoggerFactory.getLogger(HttpSparqlEndpoint.class.getName());
    private LocalServerChannel localChannel;

    public HttpSparqlEndpoint(LocalServerChannel localChannel){
        this.localChannel = localChannel;
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
            throws Exception{

        HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK, getHtmlContent(), "text/html");

        writeHttpResponse(channel, httpResponse, clientAddress);
    }


    private void processPost(final Channel channel, final HttpRequest httpRequest,
                             final InetSocketAddress clientAddress) throws Exception{

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);
        Query sparqlQuery = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("sparqlQuery")).getValue());

        Futures.addCallback(executeSparqlQuery(sparqlQuery), new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(@Nullable ResultSet resultSet) {
                SparqlQueryResultMessage resultMessage = new SparqlQueryResultMessage(resultSet);
                Channels.write(channel, resultMessage, clientAddress);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage());

                writeHttpResponse(channel, httpResponse, clientAddress);
            }
        });
    }


    private SettableFuture<ResultSet> executeSparqlQuery(Query sparqlQuery) throws Exception{

        SettableFuture<ResultSet> sparqlResultFuture = SettableFuture.create();
        SparqlQueryMessage sparqlQueryMessage = new SparqlQueryMessage(sparqlQuery, sparqlResultFuture);
        Channels.write(this.localChannel, sparqlQueryMessage);

        return sparqlResultFuture;
    }

    private ChannelBuffer getHtmlContent() throws IOException {
        String htmlPath = "html/sparql/sparql-endpoint.html";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(htmlPath);
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
