package eu.spitfire.ssp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.files.FilesBackendComponentFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import eu.spitfire.ssp.server.channels.SmartServiceProxyPipelineFactory;
import eu.spitfire.ssp.server.channels.handler.HttpRequestDispatcher;
import eu.spitfire.ssp.server.channels.handler.MqttResourceHandler;
import eu.spitfire.ssp.server.channels.handler.cache.DummySemanticCache;
import eu.spitfire.ssp.server.channels.handler.cache.JenaTdbSemanticCache;
import eu.spitfire.ssp.server.channels.handler.cache.SemanticCache;
import org.apache.commons.configuration.Configuration;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
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
public class ComponentFactory {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String sspHostName;
    private int sspHttpPort;

    private ScheduledExecutorService scheduledExecutorService;
    private OrderedMemoryAwareThreadPoolExecutor ioExecutorService;

    private ServerBootstrap serverBootstrap;
    private LocalPipelineFactory localPipelineFactory;

    private ExecutionHandler executionHandler;
    private HttpRequestDispatcher httpRequestDispatcher;
    private MqttResourceHandler mqttResourceHandler;
    private SemanticCache semanticCache;

    private Collection<BackendComponentFactory> backendComponentFactories;

    public ComponentFactory(Configuration config) throws Exception{
        this.sspHostName = config.getString("SSP_HOST_NAME");
        this.sspHttpPort = config.getInt("SSP_HTTP_SERVER_PORT", 8080);
        //Create Executor Services
        createScheduledExecutorService();
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

    public ServerBootstrap getServerBootstrap(){
        return this.serverBootstrap;
    }


    public Collection<BackendComponentFactory> getBackendComponentFactories(){
        return this.backendComponentFactories;
    }

    private void createBackendComponentFactories(Configuration config) throws Exception {
        String[] enabledBackends = config.getStringArray("ENABLE_BACKEND");

        this.backendComponentFactories = new ArrayList<>(enabledBackends.length);

        for(String proxyServiceManagerName : enabledBackends){

            if(proxyServiceManagerName.equals("coap")) {
                log.info("Create CoAP Backend");
                InetAddress registrationServerAddress =
                        InetAddress.getByName(config.getString("coap.registration.server.ip"));
                BackendComponentFactory backendComponentFactory =
                        new CoapBackendComponentFactory("coap", localPipelineFactory, scheduledExecutorService,
                                sspHostName, sspHttpPort, registrationServerAddress);
                this.backendComponentFactories.add(backendComponentFactory);
                continue;
            }

            //Local files_OLD
            else if(proxyServiceManagerName.equals("files")){
                String directory = config.getString("files.directory");
                if(directory == null){
                    throw new Exception("Property 'files.directory' not set.");
                }
                boolean copyExamples = config.getBoolean("files.copyExamples");
                int numberOfRandomFiles = config.getInt("files.numberOfRandomFiles", 0);
                BackendComponentFactory backendComponentFactory =
                        new FilesBackendComponentFactory("files",localPipelineFactory, scheduledExecutorService,
                                sspHostName, sspHttpPort, Paths.get(directory));
                this.backendComponentFactories.add(backendComponentFactory);
                continue;

            }
//            else if(proxyServiceManagerName.equals("uberdust")){
//                log.info("Create Uberdust Gateway.");
//                proxyServiceManager =
//                        new UberdustBackendManager("uberdust", internalChannel, resourceMgmtExecutorService);
//            }

            //Unknown AbstractGatewayFactory type
            else {
                log.error("Config file error: Gateway for '" + proxyServiceManagerName + "' not found!");
                continue;
            }
        }
    }


    private void createLocalPipelineFactory(){
        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();
        handler.add(semanticCache);
        if(!(mqttResourceHandler == null))
            handler.add(mqttResourceHandler);

        handler.add(httpRequestDispatcher);
        this.localPipelineFactory = new LocalPipelineFactory(handler);
        log.debug("Local Pipeline Factory created.");
    }

    private void createServerBootstrap(Configuration config) throws Exception {
        //read parameters from config
        boolean tcpNoDelay = config.getBoolean("SSP_TCP_NODELAY");
        int ioThreads = config.getInt("SSP_I/O_THREADS");

        //create the bootstrap
        this.serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(), ioThreads
        ));

        this.serverBootstrap.setOption("tcpNoDelay", tcpNoDelay);

        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();
        handler.add(executionHandler);
        handler.add(semanticCache);
        if(!(mqttResourceHandler == null))
            handler.add(mqttResourceHandler);

        handler.add(httpRequestDispatcher);

        this.serverBootstrap.setPipelineFactory(new SmartServiceProxyPipelineFactory(handler));
        log.debug("Server Bootstrap created.");
    }

    private void createIoExecutorService(Configuration config){
        int executionThreads = config.getInt("SSP_REQUEST_EXECUTION_THREADS");
        int messageQueueSize = config.getInt("SSP_MESSAGE_QUEUE_SIZE");;

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP-I/O-Thread #%d")
                .build();

        this.ioExecutorService = new OrderedMemoryAwareThreadPoolExecutor(executionThreads, 0, messageQueueSize,
                        60, TimeUnit.SECONDS, threadFactory);

        log.debug("I/O Executor Service created.");
    }


    private void createExecutionHandler(){
        this.executionHandler = new ExecutionHandler(this.ioExecutorService);
        log.debug("Execution Handler created.");
    }


    private void createHttpRequestDispatcher() throws Exception {
        this.httpRequestDispatcher = new HttpRequestDispatcher(ioExecutorService, semanticCache.supportsSPARQL(),
                semanticCache, sspHostName, sspHttpPort);
        log.debug("HTTP Request Dispatcher created.");
    }


    private void createMqttResourceHandler(Configuration config) throws Exception {
        if(config.getBoolean("ENABLE_MQTT", false)){
            String mqttBrokerUri = config.getString("MQTT_BROKER_URI");
            int mqttBrokerHttpPort = config.getInt("MQTT_BROKER_HTTP_PORT");
            this.mqttResourceHandler = new MqttResourceHandler(mqttBrokerUri, mqttBrokerHttpPort);
            log.debug("MQTT Handler created.");
        }
        else{
            this.mqttResourceHandler = null;
            log.debug("MQTT was disabled.");
        }
    }


    private void createSemanticCache(Configuration config){
        String cacheType = config.getString("cache");

        if("dummy".equals(cacheType)){
            this.semanticCache = new DummySemanticCache(scheduledExecutorService);
            log.info("Semantic Cache is of type {}", this.semanticCache.getClass().getSimpleName());
            return;
        }

        if("jenaTDB".equals(cacheType)){
            String dbDirectory = config.getString("cache.jenaTDB.dbDirectory");
            if(dbDirectory == null)
                throw new RuntimeException("'cache.jenaSDB.jdbc.url' missing in ssp.properties");

            this.semanticCache =  new JenaTdbSemanticCache(scheduledExecutorService, Paths.get(dbDirectory));
            return;
        }

        throw new RuntimeException("No cache type defined in ssp.properties");
    }


    private void createScheduledExecutorService(){
        //Scheduled executorservice for management tasks
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Proxy-Service-Mgmt.-Thread #%d")
                .build();
        int numberOfMgmtThreads = Math.max(Runtime.getRuntime().availableProcessors() * 2, 4);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(numberOfMgmtThreads, threadFactory);
        log.debug("Scheduled Executor created.");
    }
}
