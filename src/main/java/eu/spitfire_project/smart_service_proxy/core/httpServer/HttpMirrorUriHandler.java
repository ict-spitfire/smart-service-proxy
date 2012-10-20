/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire_project.smart_service_proxy.core.httpServer;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

/**
 * This handler translates HTTPRequests which
 * are targeted to wildcard DNS entries.
 *
 * @author Stefan Hueske
 * @author Oliver Kleine
 */
public class HttpMirrorUriHandler extends SimpleChannelUpstreamHandler {

    private static Logger log =  Logger.getLogger(HttpMirrorUriHandler.class.getName());

    private static Configuration config;
    static{
        try {
            config = new PropertiesConfiguration("ssp.properties");
        } catch (ConfigurationException e) {
            log.error("Error while loading configuration.", e);
        }
    }

    private final String DNS_WILDCARD_POSTFIX = config.getString("IPv4_SERVER_DNS_WILDCARD_POSTFIX");

//    private static HttpMirrorUriHandler instance = new HttpMirrorUriHandler();
//
//    private HttpMirrorUriHandler(){
//
//    }
//    /**
//     * Returns the new HttpMirrorUriHandler instance
//     *
//     */
//    public static HttpMirrorUriHandler getInstance(){
//        return instance;
//    }

    /**
     * Changes the requests host part to the IPv6 address of the target resource. This is to enable IPv4 clients to
     * access IPv6 resources. E.g. a request for the resource http://2001-638--1.example.org/path would be transparently
     * redirected to http://[2001:638::1]/path
     *
     * @param ctx The ChannelHandlerContext
     * @param me The MessageEvent
     * @throws Exception
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)  throws Exception{
        if (me.getMessage() instanceof HttpRequest) {

            HttpRequest request = (HttpRequest) me.getMessage();
            String host = request.getHeader(HOST);
            log.debug("Incoming HTTP request for host: " + host);

            //Eventually convert URIs host part to IPv6 address of the target host
            if (host.contains(DNS_WILDCARD_POSTFIX)) {
                host = host.substring(0, host.indexOf(DNS_WILDCARD_POSTFIX) - 1);
                host = host.replace("-", ":");
                log.debug("New target host: " + host);

                try{
                    request.setHeader(HOST,
                                      "[" + InetAddress.getByName("[" + host + "]").getHostAddress() + "]");
                }
                catch (UnknownHostException e) {
                    log.debug("Not an IPv6 address: " + host);
                }
            }
        }

        ctx.sendUpstream(me);

    }
}
