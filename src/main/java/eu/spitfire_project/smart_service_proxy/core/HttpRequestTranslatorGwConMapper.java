package eu.spitfire_project.smart_service_proxy.core;


import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionMapper;
import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionTable;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;


/*
 * This handler translates HTTPRequests which
 * are directly targeted to a sensor. These requests
 * will be converted transparently from the clients point of view.
 *
 * @author Stefan Hueske
 * @author Oliver Kleine
 * */
 

public class HttpRequestTranslatorGwConMapper extends SimpleChannelUpstreamHandler {

    private static Logger log = Logger.getLogger(HttpRequestTranslatorGwConMapper.class.getName());
    
    private ConnectionTable connectionTable = ConnectionTable.getInstance();
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception{
        InetSocketAddress remoteAddress =
                (InetSocketAddress) ctx.getChannel().getRemoteAddress();
        
        //check if request comes from tun interface
        if (!remoteAddress.getAddress().equals(InetAddress.getByName(ConnectionMapper.tunVirtualTcpIP))
                || !(e.getMessage() instanceof HttpRequest)) {
            ctx.sendUpstream(e);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) e.getMessage();
        
        log.debug("[HttpRequestTranslatorGwConMapper]\n" +
                "\t Original sender address: " + connectionTable.getTcpRequest(remoteAddress.getPort()) + "\n" +
                "\t TUN sender address: " + remoteAddress + "\n" +
                "\t Original receipient address: " + httpRequest.getHeader(HOST));
        
        
        String sensorIP = connectionTable.getTcpRequest(remoteAddress.getPort())
                .getDestIP().getHostAddress();

        httpRequest.setHeader(HOST, "[" + sensorIP + "]");
        
        log.debug("[HttpRequestTranslatorGwConMapper]\n" +
                "\t New receipient address: "+  httpRequest.getHeader(HOST));

        ctx.sendUpstream(e);
    }

}
