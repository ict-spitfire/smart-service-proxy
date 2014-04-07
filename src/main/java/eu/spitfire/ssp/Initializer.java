package eu.spitfire.ssp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.files.FilesBackendComponentFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.BackendResourceManager;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import eu.spitfire.ssp.server.channels.SmartServiceProxyPipelineFactory;
import eu.spitfire.ssp.server.channels.handler.HttpRequestDispatcher;
import eu.spitfire.ssp.server.channels.handler.MqttResourceHandler;
import eu.spitfire.ssp.server.channels.handler.cache.DummySemanticCache;
import eu.spitfire.ssp.server.channels.handler.cache.JenaSdbSemanticCache;
import eu.spitfire.ssp.server.channels.handler.cache.JenaTdbSemanticCache;
import eu.spitfire.ssp.server.channels.handler.cache.SemanticCache;

import org.apache.commons.configuration.Configuration;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalChannel;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.channel.local.LocalServerChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

import java.net.InetSocketAddress;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.10.13
 * Time: 21:07
 * To change this template use File | Settings | File Templates.
 */
public class Initializer {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Configuration config;

    private ScheduledExecutorService mgmtExecutorService;
    private OrderedMemoryAwareThreadPoolExecutor ioExecutorService;
    private ServerBootstrap serverBootstrap;

    private LocalPipelineFactory localPipelineFactory;

    private ExecutionHandler executionHandler;
    private HttpRequestDispatcher httpRequestDispatcher;
    private MqttResourceHandler mqttResourceHandler;
    private SemanticCache semanticCache;

    private Collection<BackendComponentFactory> backendComponentFactories;

    public Initializer(Configuration config) throws Exception {
        this.config = config;

        //Create Executor Services
        createMgmtExecutorService(config);
        createIoExecutorService(config);

        //Create Pipeline Components
        createExecutionHandler();
        createSemanticCache(config);
        createMqttResourceHandler(config);
        createHttpRequestDispatcher();

        createServerBootstrap(config);
        createLocalPipelineFactory();

        //Create backend component factories
        createBackendComponentFactories(config);
    }


    private void createMgmtExecutorService(Configuration config) {

        //Scheduled Executor Service for management tasks, i.e. everything that is not I/O
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Request Processing Thread #%d")
                .build();

        int threadCount = Math.max(Runtime.getRuntime().availableProcessors() * 2,
                config.getInt("SSP_MGMT_THREADS", 0));

        this.mgmtExecutorService = Executors.newScheduledThreadPool(threadCount, threadFactory);
        log.info("Management Executor Service created with {} threads.", threadCount);
    }


    private void createIoExecutorService(Configuration config) {

        int threadCount = Math.max(Runtime.getRuntime().availableProcessors() * 2,
                config.getInt("SSP_I/O_THREADS", 0));

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP-REQUEST-EXECUTION-Thread #%d")
                .build();

        this.ioExecutorService = new OrderedMemoryAwareThreadPoolExecutor(threadCount, 0, 0, 60, TimeUnit.SECONDS,
                threadFactory);

        log.info("I/O-Executor-Service created with {} threads", threadCount);
    }

    private void createExecutionHandler() {

        this.executionHandler = new ExecutionHandler(this.ioExecutorService);
        log.debug("Execution Handler created.");
    }


    public void start(){
        //Start proxy server
        int port = this.config.getInt("SSP_HTTP_SERVER_PORT", 8080);
        this.serverBootstrap.bind(new InetSocketAddress(port));
        log.info("HTTP proxy started (listening on port {})", port);
    }


    public Collection<BackendComponentFactory> getBackendComponentFactories() {
        return this.backendComponentFactories;
    }


    private void createBackendComponentFactories(Configuration config) throws Exception {
//        int threadsPerBackand = config.getInt("SSP_THREADS_PER_BACKEND", 4);
        String[] enabledBackends = config.getStringArray("ENABLE_BACKEND");

        this.backendComponentFactories = new ArrayList<>(enabledBackends.length);

        LocalServerChannelFactory localChannelFactory = new DefaultLocalServerChannelFactory();

        for (String backendName : enabledBackends) {

            BackendComponentFactory backendComponentFactory;
            switch (backendName) {

                case "files": {
                    backendComponentFactory = new FilesBackendComponentFactory("files", config,
                            this.mgmtExecutorService);
                    break;
                }

                case "coap": {
                    backendComponentFactory = new CoapBackendComponentFactory("coap", config,
                            this.mgmtExecutorService);
                    break;
                }

                //Unknown AbstractGatewayFactory type
                default: {
                    log.error("Config file error: Gateway for '" + backendName + "' not found!");
                    continue;
                }
            }

            BackendResourceManager backendResourceManager = backendComponentFactory.getBackendResourceManager();
            ChannelPipeline localPipeline = localPipelineFactory.getPipeline();

            localPipeline.addLast("Backend Resource Manager (" + backendName + ")", backendResourceManager);
            LocalServerChannel localChannel = localChannelFactory.newChannel(localPipeline);
            backendComponentFactory.setLocalChannel(localChannel);

            this.backendComponentFactories.add(backendComponentFactory);

        }
    }


    private void createLocalPipelineFactory() {
        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();
        if (!(mqttResourceHandler == null))
            handler.add(mqttResourceHandler);

        handler.add(semanticCache);
        handler.add(httpRequestDispatcher);

        this.localPipelineFactory = new LocalPipelineFactory(handler);
        log.debug("Local Pipeline Factory created.");
    }


    private void createServerBootstrap(Configuration config) throws Exception {
        //read parameters from config
        boolean tcpNoDelay = config.getBoolean("SSP_TCP_NODELAY", false);
        int ioThreads = config.getInt("SSP_I/O_THREADS");

        //create the bootstrap
        this.serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(), ioThreads)
        );

        this.serverBootstrap.setOption("reuseAddress", true);
        this.serverBootstrap.setOption("tcpNoDelay", tcpNoDelay);

        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();

        handler.add(executionHandler);

        if (!(mqttResourceHandler == null))
            handler.add(mqttResourceHandler);

        handler.add(semanticCache);
        handler.add(httpRequestDispatcher);

        this.serverBootstrap.setPipelineFactory(new SmartServiceProxyPipelineFactory(handler));
        log.debug("Server Bootstrap created.");
    }


    private void createHttpRequestDispatcher() throws Exception {
        this.httpRequestDispatcher = new HttpRequestDispatcher(ioExecutorService, semanticCache, config);
        log.debug("HTTP Request Dispatcher created.");
    }


    private void createMqttResourceHandler(Configuration config) throws Exception {
        if (config.getBoolean("ENABLE_MQTT", false)) {
            String mqttBrokerUri = config.getString("MQTT_BROKER_URI");
            int mqttBrokerHttpPort = config.getInt("MQTT_BROKER_HTTP_PORT");
            this.mqttResourceHandler = new MqttResourceHandler(mqttBrokerUri, mqttBrokerHttpPort);
            log.debug("MQTT Handler created.");
        } else {
            this.mqttResourceHandler = null;
            log.debug("MQTT was disabled.");
        }
    }


    private void createSemanticCache(Configuration config) throws Exception{
        String cacheType = config.getString("cache");

        if ("dummy".equals(cacheType)) {
            this.semanticCache = new DummySemanticCache(this.mgmtExecutorService);
            log.info("Semantic Cache is of type {}", this.semanticCache.getClass().getSimpleName());
            return;
        }

        if ("jenaTDB".equals(cacheType)) {
            String dbDirectory = config.getString("cache.jenaTDB.dbDirectory");
            if (dbDirectory == null)
                throw new RuntimeException("'cache.jenaSDB.jdbc.url' missing in ssp.properties");

            this.semanticCache = new JenaTdbSemanticCache(this.mgmtExecutorService, Paths.get(dbDirectory));
            return;
        }

        if("jenaSDB".equals(cacheType)){
            String jdbcUri = config.getString("cache.jenaSDB.jdbc.url");
            String jdbcUser = config.getString("cache.jenaSDB.jdbc.user");
            String jdbcPassword = config.getString("cache.jenaSDB.jdbc.password");

            this.semanticCache = new JenaSdbSemanticCache(this.mgmtExecutorService, jdbcUri, jdbcUser, jdbcPassword);
            return;
        }

        throw new RuntimeException("No cache type defined in ssp.properties");
    }



}
