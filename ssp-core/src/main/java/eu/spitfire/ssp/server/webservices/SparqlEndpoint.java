package eu.spitfire.ssp.server.webservices;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.internal.messages.requests.QueryTask;
import eu.spitfire.ssp.server.internal.messages.responses.QueryResult;
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
        Query query = QueryFactory.create(((MixedAttribute) decoder.getBodyHttpData("query")).getValue());

        //Execute SPARQL query, await the result and send it to the client
        Futures.addCallback(executeQuery(query), new FutureCallback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                QueryResult queryResult = new QueryResult(resultSet);
                ChannelFuture future = Channels.write(channel, queryResult, clientAddress);
                future.addListener(ChannelFutureListener.CLOSE);
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


    private SettableFuture<ResultSet> executeQuery(Query query) throws Exception{

        SettableFuture<ResultSet> resultSetFuture = SettableFuture.create();
        QueryTask queryTask = new QueryTask(query, resultSetFuture);
        Channels.write(this.localChannel, queryTask);

        return resultSetFuture;
    }
}
