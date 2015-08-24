package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.message.InternalQueryExecutionRequest;
import eu.spitfire.ssp.server.internal.QueryExecutionResults;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.MixedAttribute;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 01.07.14.
 */
public class SparqlEndpoint extends HttpWebservice{

    private LocalServerChannel localChannel;

    public SparqlEndpoint(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor,
                          LocalServerChannel localChannel){

        super(ioExecutor, internalTasksExecutor, "html/services/sparql-endpoint.html");
        this.localChannel = localChannel;
    }


    @Override
    public void processPost(final Channel channel, final HttpRequest httpRequest,
                            final InetSocketAddress clientAddress) throws Exception{

        //Decode SPARQL query from POST request
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(httpRequest);

        //String query = ((MixedAttribute) decoder.getBodyHttpData("query")).getValue();
        Query query = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("query")).getValue());

        //Execute SPARQL query, await the result and send it to the client

        Futures.addCallback(executeQuery(query), new FutureCallback<QueryExecutionResults>() {
            @Override
            public void onSuccess(QueryExecutionResults results) {

                ChannelFuture future = Channels.write(channel, results, clientAddress);
                future.addListener(ChannelFutureListener.CLOSE);

//                ResultSet resultSet = results.getResultSet();
//                ChannelFuture future = Channels.write(channel, resultSet, clientAddress);
//                future.addListener(ChannelFutureListener.CLOSE);
            }

            @Override
            public void onFailure(Throwable t) {
                HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                        httpRequest.getProtocolVersion(), status, t.getMessage()
                );

                writeHttpResponse(channel, httpResponse, clientAddress);
            }
        });
    }


    private SettableFuture<QueryExecutionResults> executeQuery(Query query) throws Exception{

        InternalQueryExecutionRequest executionRequest = new InternalQueryExecutionRequest(query);

        Channels.write(this.localChannel, executionRequest);

        return executionRequest.getResultsFuture();
    }
}
