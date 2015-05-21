package eu.spitfire.ssp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import eu.spitfire.ssp.backends.external.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.external.n3files.N3FileBackendComponentFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.internal.se.SemanticEntityBackendComponentFactory;
import eu.spitfire.ssp.backends.internal.vs.VirtualSensorBackendComponentFactory;
import eu.spitfire.ssp.server.handler.cache.*;
import eu.spitfire.ssp.server.internal.messages.requests.WebserviceRegistration;
import eu.spitfire.ssp.server.handler.HttpRequestDispatcher;
import eu.spitfire.ssp.server.pipelines.HttpProxyPipelineFactory;
import eu.spitfire.ssp.server.webservices.*;
import eu.spitfire.ssp.server.webservices.Styles;
import eu.spitfire.ssp.server.pipelines.InternalPipelineFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.jboss.netty.channel.local.LocalServerChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.10.13
 * Time: 21:07
 * To change this template use File | Settings | File Templates.
 */
public abstract class Initializer {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Configuration config;

    private ScheduledExecutorService internalTasksExecutor;
    private OrderedMemoryAwareThreadPoolExecutor ioExecutor;
    private ServerBootstrap serverBootstrap;

    private LocalServerChannelFactory localChannelFactory;
    private InternalPipelineFactory internalPipelineFactory;

    private ExecutionHandler executionHandler;
    private HttpRequestDispatcher httpRequestDispatcher;
    protected SemanticCache semanticCache;

    private Collection<BackendComponentFactory> componentFactories;

    public Initializer(String configPath) throws Exception {
        //initialize logging
        try{
            LoggingConfiguration.configureLogging("log4j.xml");
        }
        catch(Exception ex){
            LoggingConfiguration.configureDefaultLogging();
        }


        this.config = new PropertiesConfiguration(configPath);

        log.info("START SSP!");
        this.localChannelFactory = new DefaultLocalServerChannelFactory();

        //Create Executor Services
        createInternalTasksExecutorService();
        createIoExecutorService();

        //Create Pipeline Components
        createMqttResourceHandler(config);
        this.semanticCache = createSemanticCache(this.config);
        createHttpRequestDispatcher();

        //create local pipeline factory
        createLocalPipelineFactory();

        //create I/O channel
        createExecutionHandler();
        createServerBootstrap();

        //Create backend component factories
        createBackendComponentFactories();

        //Create and register initial Webservices
        registerHomepage();
        registerFavicon();
        registerSparqlEndpoint();

        //this is just an example on what is possible...
        registerTrafficMonitoring();

        //Start the backends
        for (BackendComponentFactory componentFactory : this.getComponentFactories()) {
            componentFactory.createComponents(config);
        }

        log.info("SSP succesfully started!");
    }


    private void createInternalTasksExecutorService() {

        int threads = this.config.getInt("ssp.threads.internal", 0);

        //Scheduled Executor Service for management tasks, i.e. everything that is not I/O
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP Internal Thread #%d")
                .build();

        int threadCount = Math.max(Runtime.getRuntime().availableProcessors() * 2, threads);

        this.internalTasksExecutor = Executors.newScheduledThreadPool(threadCount, threadFactory);
        log.info("Management Executor Service created with {} threads.", threadCount);
    }


    private void createIoExecutorService() {

        int threads = this.config.getInt("ssp.threads.io", 0);

        int threadCount = Math.max(Runtime.getRuntime().availableProcessors() * 2, threads);

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("SSP I/O Thread #%d")
                .build();

        this.ioExecutor = new OrderedMemoryAwareThreadPoolExecutor(threadCount, 0, 0, 60, TimeUnit.SECONDS,
                threadFactory);

        log.info("I/O-Executor-Service created with {} threads", threadCount);
    }


    private void createExecutionHandler() {
        this.executionHandler = new ExecutionHandler(this.ioExecutor);
        log.debug("Execution Handler created.");
    }


    public void initialize(){
        //Start proxy server
        int port = this.config.getInt("SSP_HTTP_PORT", 8080);
        this.serverBootstrap.bind(new InetSocketAddress(port));
        log.info("HTTP proxy started (listening on port {})", port);
    }


    public Collection<BackendComponentFactory> getComponentFactories() {
        return this.componentFactories;
    }


    private void createBackendComponentFactories() throws Exception {
        this.componentFactories = new ArrayList<>();

        //Add backend for semantic entities (default)
        ChannelPipeline localPipeline = internalPipelineFactory.getPipeline();
        LocalServerChannel localChannel = localChannelFactory.newChannel(localPipeline);
        this.componentFactories.add(new SemanticEntityBackendComponentFactory(
                this.config, localChannel, this.internalTasksExecutor, this.ioExecutor)
        );

        //Add backend for virtual sensors (default)
        localPipeline = internalPipelineFactory.getPipeline();
        localChannel = localChannelFactory.newChannel(localPipeline);
        this.componentFactories.add(new VirtualSensorBackendComponentFactory(
                this.config, localChannel, this.internalTasksExecutor, this.ioExecutor)
        );

        //Add N3 file backend
        if(this.config.getBoolean("n3files.enabled", false)){
            localPipeline = internalPipelineFactory.getPipeline();
            localChannel = localChannelFactory.newChannel(localPipeline);

            this.componentFactories.add(new N3FileBackendComponentFactory(
                    this.config, localChannel, this.internalTasksExecutor, this.ioExecutor)
            );
        }

        //Add CoAP backend
        if(this.config.getBoolean("coap.enabled", false)){
            localPipeline = internalPipelineFactory.getPipeline();
            localChannel = localChannelFactory.newChannel(localPipeline);

            this.componentFactories.add(new CoapBackendComponentFactory(
                    this.config, localChannel, this.internalTasksExecutor, this.ioExecutor)
            );
        }
    }


    private void createLocalPipelineFactory() {
        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();
//        if (!(mqttHandler == null))
//            handler.add(mqttHandler);

        handler.add(semanticCache);
        handler.add(httpRequestDispatcher);

        this.internalPipelineFactory = new InternalPipelineFactory(handler);
        log.debug("Local Pipeline Factory created.");
    }


    private void createServerBootstrap() throws Exception {

        //create the bootstrap
        Executor ioExecutor = Executors.newCachedThreadPool();
        this.serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                ioExecutor)
        );

        this.serverBootstrap.setOption("reuseAddress", true);
        this.serverBootstrap.setOption("tcpNoDelay", true);

        LinkedHashSet<ChannelHandler> handler = new LinkedHashSet<>();

        handler.add(executionHandler);

//        if (!(mqttHandler == null))
//            handler.add(mqttHandler);

        handler.add(semanticCache);
        handler.add(httpRequestDispatcher);

        this.serverBootstrap.setPipelineFactory(new HttpProxyPipelineFactory(handler));
        log.debug("Server Bootstrap created.");
    }


    private void createHttpRequestDispatcher() throws Exception {
        Styles styleWebservice = new Styles(
                this.ioExecutor, this.internalTasksExecutor, null
        );

        this.httpRequestDispatcher = new HttpRequestDispatcher(styleWebservice);
        log.debug("HTTP Request Dispatcher created.");
    }


    private void createMqttResourceHandler(Configuration config) throws Exception {
        if(config.getBoolean("mqtt.enabled", false)){
            String brokerURI = config.getString("mqtt.broker.uri");
            int brokerHttpPort = config.getInt("mqtt.broker.http.port");

            //TODO
//            this.mqttHandler = new MqttHandler(brokerUri, brokerPort);
//            log.debug("MQTT Handler created.");
        }
        else{
            log.info("MQTT was disabled (No broker URI given).");
        }
    }

    public abstract SemanticCache createSemanticCache(Configuration config);

//    private void createSemanticCache(Configuration config) throws Exception{
//        String cacheType = config.getString("cache");
//
//        if ("dummy".equals(cacheType)) {
//            this.semanticCache = new DummySemanticCache(this.ioExecutor, this.internalTasksExecutor);
//            log.info("Semantic Cache is of type {}", this.semanticCache.getClass().getSimpleName());
//            return;
//        }
//
//        if ("jenaTDB".equals(cacheType)) {
//            String dbDirectory = config.getString("cache.jenaTDB.dbDirectory");
//            if (dbDirectory == null)
//                throw new RuntimeException("'cache.jenaTDB.dbDirectory' missing in ssp.properties");
//
//            String spatialIndexDirectory = config.getString("cache.spatial.index.directory");
//            if (spatialIndexDirectory == null)
//                throw new RuntimeException("'cache.spatial.index.directory' missing in ssp.properties");
//
//            Path directoryPath = Paths.get(dbDirectory);
//            Path spatialIndexDirectoryPath = Paths.get(spatialIndexDirectory);
//
//            if(!Files.isDirectory(directoryPath))
//                throw new IllegalArgumentException("The given path for Jena TDB does not refer to a directory!");
//
//            this.semanticCache = new JenaTdbSemanticCache(this.ioExecutor, this.internalTasksExecutor,
//                    directoryPath, spatialIndexDirectoryPath);
//
//            return;
//        }
//
//        if("luposdate".equals(cacheType)){
//            this.semanticCache = new LuposdateSemanticCache(this.ioExecutor, this.internalTasksExecutor) ;
//            return;
//
//        }
//
//        if("parliament".equals(cacheType)){
//            this.semanticCache = new ParliamentSemanticCache(this.ioExecutor, this.internalTasksExecutor);
//            return;
//        }
////        if("jenaSDB".equals(cacheType)){
////            String jdbcUri = config.getString("cache.jenaSDB.jdbc.url");
////            String jdbcUser = config.getString("cache.jenaSDB.jdbc.user");
////            String jdbcPassword = config.getString("cache.jenaSDB.jdbc.password");
////
////            this.semanticCache = new JenaSdbSemanticCache(this.internalTasksExecutor, jdbcUri, jdbcUser, jdbcPassword);
////            return;
////        }
//
//        throw new RuntimeException("No cache type defined in ssp.properties");
//    }


    private void registerHomepage() throws Exception{
        registerHttpWebservice(
                new URI(null, null, null, -1, "/", null, null),
                new Homepage(this.ioExecutor, this.internalTasksExecutor)
        );
    }

    /**
     * Registers the service to provide the favicon.ico
     */
    private void registerFavicon() throws Exception {
        URI uri = new URI(null, null, null, -1, "/favicon.ico", null, null);
        HttpWebservice httpWebservice = new Favicon(this.ioExecutor, this.internalTasksExecutor);
        registerHttpWebservice(uri, httpWebservice);
    }


    private void registerSparqlEndpoint() throws Exception{
        URI uri = new URI(null, null, null, -1, "/services/sparql-endpoint", null, null);

        LocalServerChannel localChannel = this.localChannelFactory.newChannel(
                this.internalPipelineFactory.getPipeline()
        );

        HttpWebservice httpWebservice = new SparqlEndpoint(
                this.ioExecutor, this.internalTasksExecutor, localChannel
        );

        registerHttpWebservice(uri, httpWebservice);
    }


    private void registerTrafficMonitoring() throws Exception{
        URI uri = new URI(null, null, null, -1, "/services/geo-views/traffic-monitoring", null, null);

        LocalServerChannel localChannel = this.localChannelFactory.newChannel(
                this.internalPipelineFactory.getPipeline()
        );

        HttpWebservice httpWebservice = new TrafficMonitoring(
                this.ioExecutor, this.internalTasksExecutor
        );

        registerHttpWebservice(uri, httpWebservice);

    }

    private void registerHttpWebservice(final URI webserviceUri, HttpWebservice httpWebservice) throws Exception{

        LocalServerChannel localChannel = localChannelFactory.newChannel(internalPipelineFactory.getPipeline());
        WebserviceRegistration registrationMessage = new WebserviceRegistration(webserviceUri,
                httpWebservice);

        ChannelFuture future = Channels.write(localChannel, registrationMessage);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess())
                    log.info("Successfully registered HTTP Webservice at URI {}", webserviceUri);
                else
                    log.error("Could not register HTTP Webservice!", future.getCause());
            }
        });

    }

    protected ScheduledExecutorService getInternalTasksExecutor() {
        return this.internalTasksExecutor;
    }

    protected ExecutorService getIoExecutor() {
        return this.ioExecutor;
    }

}
