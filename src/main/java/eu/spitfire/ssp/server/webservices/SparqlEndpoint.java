package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import eu.spitfire.ssp.server.common.messages.QueryTask;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.MixedAttribute;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 01.07.14.
 */
public class SparqlEndpoint extends HttpWebservice{

    private LocalServerChannel localChannel;

    public SparqlEndpoint(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
                          LocalServerChannel localChannel){

        super(ioExecutor, internalTasksExecutor, "html/sparql/sparql-endpoint.html");
        this.localChannel = localChannel;
    }


    @Override
    public void processPost(final Channel channel, final HttpRequest httpRequest,
                            final InetSocketAddress clientAddress) throws Exception{

        //Decode SPARQL query from POST request
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);
        Query sparqlQuery = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("sparqlQuery")).getValue());

        //Execute SPARQL query, await the result and send it to the client
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
        QueryTask queryTask = new QueryTask(sparqlQuery, sparqlResultFuture);
        Channels.write(this.localChannel, queryTask);

        return sparqlResultFuture;
    }
}
