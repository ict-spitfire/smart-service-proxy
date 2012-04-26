package eu.spitfire_project.smart_service_proxy.core;


import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionMapper;
import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionTable;
import eu.spitfire_project.smart_service_proxy.Main;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.GapContent;
import org.apache.log4j.Priority;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;

/**
 * This handler translates HTTPRequests which
 * are directly targeted to a sensor. These requests
 * will be converted transparently from the clients point of view.
 *
 * @author Stefan Hueske
 */
public class HttpRequestTranslatorGwConMapper extends SimpleChannelUpstreamHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        InetSocketAddress sockAddr =
                (InetSocketAddress) ctx.getChannel().getRemoteAddress();
        //check if request comes from tun interface
        if (!sockAddr.getAddress().equals(InetAddress.getByName(ConnectionMapper.tunVirtualTcpIP))
                || !(e.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(e);
            return;
        }
        try {
            HttpRequest request = (HttpRequest) e.getMessage();

            ConnectionTable table = ConnectionTable.getInstance();

            String sensorIP = table.getTcpRequest(sockAddr.getPort())
                    .getDestIP().getHostAddress();

            request.setHeader(HOST, "[" + sensorIP + "]");
        } catch(Exception e2) {
            System.out.println(e2);
        }
        ctx.sendUpstream(e);
    }

}
