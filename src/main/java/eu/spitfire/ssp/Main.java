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
import eu.spitfire.ssp.backends.AbstractBackendManager;
import eu.spitfire.ssp.backends.coap.CoapBackendManager;
import eu.spitfire.ssp.backends.files.FilesBackendManager;
import eu.spitfire.ssp.backends.simple.SimpleBackendManager;
import eu.spitfire.ssp.server.pipeline.SmartServiceProxyPipelineFactory;
import eu.spitfire.ssp.server.pipeline.handler.cache.*;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

//import eu.spitfire.ssp.backends.simple.SimpleBackendManager;

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
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP-Execution-Thread #%d").build();

        int executionThreads = getNumberOfExecutionTreads(config);
        int ioThreads = getNumberOfIoThreads(config);
        int messageQueueSize = getMessageQueueSize(config);

        OrderedMemoryAwareThreadPoolExecutor executorService =
                new OrderedMemoryAwareThreadPoolExecutor(executionThreads, 0, messageQueueSize,
                        60, TimeUnit.SECONDS, threadFactory);


        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                                                                Executors.newCachedThreadPool(),
                                                                Executors.newCachedThreadPool(), ioThreads
        ));

//        ServerBootstrap bootstrap = new ServerBootstrap(new OioServerSocketChannelFactory(
//                Executors.newCachedThreadPool(),
//                Executors.newCachedThreadPool()
//        ));

        bootstrap.setOption("tcpNoDelay", getTcpNoDelay(config));

        //caching
        AbstractSemanticCache cache = null;
        String cacheType = config.getString("cache");
        if("simple".equals(cacheType))
            cache = new SimpleSemanticCache();
        else if("p2p".equals(cacheType))
            cache = new P2PSemanticCache();
        else if("jenaSDB".equals(cacheType)){
            String jdbcUrl = config.getString("cache.jenaSDB.jdbc.url");
            String user = config.getString("cache.jenaSDB.jdbc.user");
            String password = config.getString("cache.jenaSDB.jdbc.password");

            if(jdbcUrl == null)
                throw new NullPointerException("'cache.jenaSDB.jdbc.url' missing in ssp.properties");

            if(user == null)
                throw new NullPointerException("'cache.jenaSDB.jdbc.user' missing in ssp.properties");

            if(password == null)
                throw new NullPointerException("'cache.jenaSDB.jdbc.password' missing in ssp.properties");

            cache = new JenaSdbSemanticCache(jdbcUrl, user, password);
        }
        else if("jenaTDB".equals(cacheType)){
            String dbDirectory = config.getString("cache.jenaTDB.dbDirectory");
            if(dbDirectory == null)
                throw new NullPointerException("'cache.jenaSDB.jdbc.url' missing in ssp.properties");

            cache = new JenaTdbSemanticCache(Paths.get(dbDirectory));
        }

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
        int numberOfMgmtThreads = Math.max(Runtime.getRuntime().availableProcessors() * 2, 4);
        ScheduledExecutorService scheduledExecutorService =
                Executors.newScheduledThreadPool(numberOfMgmtThreads, threadFactory);

        String[] enabledProxyServiceManagers = config.getStringArray("enableProxyServiceManager");

        for(String proxyServiceManagerName : enabledProxyServiceManagers){

            AbstractBackendManager proxyServiceManager;

            //Simple (John Smith VCARD)
            if(proxyServiceManagerName.equals("simple")){
                log.info("Create Simple Gateway.");
                proxyServiceManager =
                        new SimpleBackendManager("simple", internalChannel, scheduledExecutorService);
            }

            //CoAP
            else if(proxyServiceManagerName.equals("coap")) {
                log.info("Create CoAP Gateway.");
                proxyServiceManager =
                        new CoapBackendManager("coap", internalChannel, scheduledExecutorService);
            }

            //Local files
            else if(proxyServiceManagerName.equals("files")){
                String directory = config.getString("files.directory");
                if(directory == null){
                    throw new Exception("Property 'files.directory' not set.");
                }
                boolean copyExamples = config.getBoolean("files.copyExamples");
                int numberOfRandomFiles = config.getInt("files.numberOfRandomFiles", 0);
                proxyServiceManager =
                        new FilesBackendManager("files", internalChannel, scheduledExecutorService, copyExamples,
                                numberOfRandomFiles, directory);


            }

            //Unknown AbstractGatewayFactory type
            else {
                log.error("Config file error: Gateway for '" + proxyServiceManagerName + "' not found!");
                continue;
            }

            proxyServiceManager.registerGuiAndInitialize();
        }
    }

    private static void initializeLogging() {
        DOMConfigurator.configure("log4j.xml");
    }


    public static int getNumberOfExecutionTreads(Configuration config){
        int executionThreads;
        try{
            executionThreads = config.getInt("SSP_REQUEST_EXECUTION_THREADS");
        }
        catch (NoSuchElementException e){
            log.error("Value of SSP_REQUEST_EXECUTION_THREADS undefined in ssp.properties.");
            throw e;
        }
        catch(ConversionException e){
            log.error("Value of SSP_REQUEST_EXECUTION_THREADS is not an integer in ssp.properties.");
            throw e;
        }
        return executionThreads;
    }

    public static int getNumberOfIoThreads(Configuration config){
        int ioThreads;
        try{
            ioThreads = config.getInt("SSP_I/O_THREADS");
        }
        catch (NoSuchElementException e){
            log.error("Value of SSP_I/O_THREADS undefined in ssp.properties.");
            throw e;
        }
        catch(ConversionException e){
            log.error("Value of SSP_I/O_THREADS is not an integer in ssp.properties.");
            throw e;
        }
        return ioThreads;
    }

    private static int getMessageQueueSize(Configuration config) {
        int messageQueueSize;
        try{
            messageQueueSize = config.getInt("SSP_MESSAGE_QUEUE_SIZE");
        }
        catch (NoSuchElementException e){
            log.error("Value ofSSP_MESSAGE_QUEUE_SIZE undefined in ssp.properties.");
            throw e;
        }
        catch(ConversionException e){
            log.error("Value of SSP_MESSAGE_QUEUE_SIZE is not an integer in ssp.properties.");
            throw e;
        }
        return messageQueueSize;
    }

    private static boolean getTcpNoDelay(Configuration config){
        boolean tcpNoDelay;
        try{
            tcpNoDelay = config.getBoolean("SSP_TCP_NODELAY");
        }
        catch (NoSuchElementException e){
            log.error("Value of SSP_TCP_NODELAY undefined in ssp.properties.");
            throw e;
        }
        catch(ConversionException e){
            log.error("Value of SSP_TCP_NODELAY :is not a boolean in ssp.properties.");
            throw e;
        }
        return tcpNoDelay;
    }
}


