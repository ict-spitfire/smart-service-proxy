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

import eu.spitfire.ssp.gateway.AbstractGateway;
import eu.spitfire.ssp.gateway.files.FilesGatewayFactory;
import eu.spitfire.ssp.gateway.simple.SimpleGatewayFactory;
import eu.spitfire.ssp.core.pipeline.SmartServiceProxyPipelineFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static Logger log = Logger.getLogger(Main.class.getName());

    public static String SSP_DNS_NAME;
    public static int SSP_HTTP_SERVER_PORT;
    public static String DNS_WILDCARD_POSTFIX;

    /**
     * @throws Exception might be everything
     */
    public static void main(String[] args) throws Exception {
        initializeLogging();
        Configuration config = new PropertiesConfiguration("ssp.properties");

        SSP_DNS_NAME = config.getString("SSP_DNS_NAME", null);
        DNS_WILDCARD_POSTFIX = config.getString("DNS_WILDCARD_POSTFIX", null);
        SSP_HTTP_SERVER_PORT = config.getInt("SSP_HTTP_SERVER_PORT", 8080);

        //create pipeline for server
        OrderedMemoryAwareThreadPoolExecutor executor = new OrderedMemoryAwareThreadPoolExecutor(20, 0, 0);
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                                                                Executors.newCachedThreadPool(),
                                                                Executors.newCachedThreadPool()
        ));
        SmartServiceProxyPipelineFactory pipelineFactory = new SmartServiceProxyPipelineFactory(executor);
        bootstrap.setPipelineFactory(pipelineFactory);

        //start server
        int httpServerPort = config.getInt("SSP_HTTP_SERVER_PORT", 8080);
        bootstrap.bind(new InetSocketAddress(httpServerPort));
        log.info("HTTP server started. Listening on port " + httpServerPort);

        //Create local channel (for internal messages)
        DefaultLocalServerChannelFactory internalChannelFactory = new DefaultLocalServerChannelFactory();
        LocalServerChannel internalChannel = internalChannelFactory.newChannel(pipelineFactory.getInternalPipeline());

        //Create enabled gateway
        createGateways(config, internalChannel, executor);
    }

    //Create the gateway enabled in ssp.properties
    private static void createGateways(Configuration config, LocalServerChannel internalChannel,
                                       ExecutorService ioExecutorService) throws Exception {

        log.debug("Start creating enabled Gateways!");
        String[] enabledGateways = config.getStringArray("enableGateway");

        for(String gatewayName : enabledGateways){

            AbstractGateway gateway;
            //CoAPBackend
//            if(gatewayName.equals("coap")) {
//                //CoapBackend coapBackend = new CoapBackend(internalChannel);
//            }

            //SimpleGatewayFactory
            if(gatewayName.equals("simple")){
                log.info("Create Simple Gateway.");
                gateway = new SimpleGatewayFactory("simple");
            }

            //FilesGateway
            else if(gatewayName.equals("files")){
                String directory = config.getString("files.directory");
                if(directory == null){
                    throw new Exception("Property 'files.directory' not set.");
                }
                gateway = new FilesGatewayFactory(directory);
            }

            //Unknown AbstractGatewayFactory Type
            else {
                log.error("Config file error: Gateway for '" + gatewayName + "' not found!");
                continue;
            }

            gateway.setInternalChannel(internalChannel);
            gateway.setExecutorService(ioExecutorService);

            gateway.registerInitialServices();
        }
    }

    private static void initializeLogging(){
        String pattern = "%-23d{yyyy-MM-dd HH:mm:ss,SSS} | %-32.32t | %-30.30c{1} | %-5p | %m%n";
        PatternLayout patternLayout = new PatternLayout(pattern);

        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new ConsoleAppender(patternLayout));

        Logger.getRootLogger().setLevel(Level.DEBUG);
    }
}


