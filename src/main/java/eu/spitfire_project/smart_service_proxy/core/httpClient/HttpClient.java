package eu.spitfire_project.smart_service_proxy.core.httpClient;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 20.10.12
 * Time: 19:21
 * To change this template use File | Settings | File Templates.
 */

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Throwables.propagate;

/**
* @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
* @author Andy Taylor (andy.taylor@jboss.org)
* @author <a href="http://gleamynode.net/">Trustin Lee</a>
*
* @version $Rev: 2189 $, $Date: 2010-02-19 18:02:57 +0900 (Fri, 19 Feb 2010) $
*/

public abstract class HttpClient extends SimpleChannelUpstreamHandler {
    private Logger log = Logger.getLogger(HttpClient.class.getName());

    private boolean readingChunks;
    private ClientBootstrap bootstrap;

    protected HttpClient(){
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newFixedThreadPool(1),
                        Executors.newFixedThreadPool(1)));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new HttpClientPipelineFactory(this));

        bootstrap.setOption("connectTimeoutMillis", 5000);
    }

    public void writeHttpRequest(InetSocketAddress targetAddress, HttpRequest httpRequest){
        try {

            ChannelFuture future = bootstrap.connect(targetAddress);
            log.debug("Trying to connect to " + targetAddress);

            Channel channel = future.awaitUninterruptibly().getChannel();
            if (!future.isSuccess()) {
                log.error("Could not connect!", future.getCause());
                throw new RuntimeException(future.getCause());
            }

            ChannelFuture writeFuture = channel.write(httpRequest);

            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    log.debug("Message written successfully.");
                }
            });
            writeFuture.await(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            throw propagate(e);
        }
    }

    @Override
    public abstract void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception;
}
