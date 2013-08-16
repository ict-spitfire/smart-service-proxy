/**
* Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
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
package eu.spitfire.ssp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import eu.spitfire.ssp.proxyservicemanagement.AbstractProxyServiceManager;
import eu.spitfire.ssp.server.pipeline.SmartServiceProxyPipelineFactory;
import eu.spitfire.ssp.server.pipeline.handler.cache.AbstractSemanticCache;
import eu.spitfire.ssp.server.pipeline.handler.cache.P2PSemanticCache;
import eu.spitfire.ssp.server.pipeline.handler.cache.SimpleSemanticCache;
import eu.spitfire.ssp.proxyservicemanagement.coap.CoapProxyServiceManager;
import eu.spitfire.ssp.proxyservicemanagement.simple.SimpleProxyServiceManager;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.*;

//import eu.spitfire.ssp.gateway.files.FilesGateway;

public class Main {

    private static Logger log = Logger.getLogger(Main.class.getName());

    public static String SSP_DNS_NAME;
    public static int SSP_HTTP_PROXY_PORT;

    /**
     * @throws Exception might be everything
     */
    public static void main(String[] args) throws Exception {
        initializeLogging();
        Configuration config = new PropertiesConfiguration("ssp.properties");

        SSP_DNS_NAME = config.getString("SSP_DNS_NAME", null);
        SSP_HTTP_PROXY_PORT = config.getInt("SSP_HTTP_PROXY_PORT", 8080);

        //create pipeline for server
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP-I/O-Thread #%d").build();
        OrderedMemoryAwareThreadPoolExecutor executorService =
                new OrderedMemoryAwareThreadPoolExecutor(20, 0, 0, 10, TimeUnit.SECONDS, threadFactory);
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                                                                Executors.newCachedThreadPool(),
                                                                Executors.newCachedThreadPool()
        ));

        //caching
        AbstractSemanticCache cache = null;
        String cacheType = config.getString("cache");
        if("simple".equals(cacheType))
            cache = new SimpleSemanticCache();
        else if("p2p".equals(cacheType))
            cache = new P2PSemanticCache();

        SmartServiceProxyPipelineFactory pipelineFactory = new SmartServiceProxyPipelineFactory(executorService, cache);
        bootstrap.setPipelineFactory(pipelineFactory);

        bootstrap.bind(new InetSocketAddress(SSP_HTTP_PROXY_PORT));
        log.info("HTTP server started. Listening on port " + SSP_HTTP_PROXY_PORT + ".");

        //Create local channel (for internal messages)
        DefaultLocalServerChannelFactory internalChannelFactory = new DefaultLocalServerChannelFactory();
        LocalServerChannel internalChannel = internalChannelFactory.newChannel(pipelineFactory.getInternalPipeline());

        //Create enabled gateway
        startProxyServiceManagers(config, internalChannel);
    }

    //Create the gateway enabled in ssp.properties
    private static void startProxyServiceManagers(Configuration config, LocalServerChannel internalChannel) throws Exception {

        log.debug("Start creating enabled ProxyServiceCreators!");

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Proxy-Service-Mgmt.-Thread #%d").build();
        ScheduledExecutorService scheduledExecutorService =
                Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2, threadFactory);


        String[] enabledProxyServiceManagers = config.getStringArray("enableProxyServiceManager");

        for(String proxyServiceManagerName : enabledProxyServiceManagers){

            AbstractProxyServiceManager abstractProxyServiceManager;
            //SimpleProxyServiceManager
            if(proxyServiceManagerName.equals("simple")){
                log.info("Create Simple Gateway.");
                abstractProxyServiceManager =
                        new SimpleProxyServiceManager("simple", internalChannel, scheduledExecutorService);

            }

            //CoAPBackend
            else if(proxyServiceManagerName.equals("coap")) {
                log.info("Create CoAP Gateway.");
                abstractProxyServiceManager =
                        new CoapProxyServiceManager("coap", internalChannel, scheduledExecutorService);
            }

            //FilesGateway
//            else if(gatewayName.equals("files")){
//                String directory = config.getString("files.directory");
//                if(directory == null){
//                    throw new Exception("Property 'files.directory' not set.");
//                }
//                abstractProxyServiceManager = new FilesGateway(directory);
//            }

            //Unknown AbstractGatewayFactory Type
            else {
                log.error("Config file error: Gateway for '" + proxyServiceManagerName + "' not found!");
                continue;
            }

            abstractProxyServiceManager.initialize();
        }
    }

    private static void initializeLogging(){
        String pattern = "%-23d{yyyy-MM-dd HH:mm:ss,SSS} | %-32.32t | %-30.30c{1} | %-5p | %m%n";
        PatternLayout patternLayout = new PatternLayout(pattern);

        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new ConsoleAppender(patternLayout));

        Logger.getRootLogger().setLevel(Level.INFO);
        Logger.getLogger("eu.spitfire.ssp.server.payloadserialization.ShdtDeserializer").setLevel(Level.ERROR);
        Logger.getLogger("de.uniluebeck.itm.ncoap").setLevel(Level.WARN);
    }
}


