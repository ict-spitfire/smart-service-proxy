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
package eu.spitfire_project.smart_service_proxy;

import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionMapper;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapNodeRegistrationServer;
import eu.spitfire_project.smart_service_proxy.backends.coap.uberdust.UberdustCoapServerBackend;
import eu.spitfire_project.smart_service_proxy.backends.files.FilesBackend;
import eu.spitfire_project.smart_service_proxy.backends.generator.GeneratorBackend;
import eu.spitfire_project.smart_service_proxy.backends.simple.SimpleBackend;
import eu.spitfire_project.smart_service_proxy.backends.slse.SLSEBackend;
import eu.spitfire_project.smart_service_proxy.backends.uberdust.UberdustBackend;
import eu.spitfire_project.smart_service_proxy.backends.wiselib_test.WiselibTestBackend;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.HttpEntityManagerPipelineFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.io.File;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;

public class Main {

    private static Logger log = Logger.getLogger(Main.class.getName());

    static{
        //Logger.getLogger("eu.spitfire_project.smart_service_proxy").addAppender(new ConsoleAppender(new SimpleLayout()));
        Logger.getRootLogger().addAppender(new ConsoleAppender(new SimpleLayout()));
        Logger.getLogger("eu.spitfire_project.smart_service_proxy").setLevel(Level.DEBUG);

        //Logger.getLogger("de.uniluebeck.itm.spitfire.gatewayconnectionmapper").addAppender(new ConsoleAppender(new SimpleLayout()));
        Logger.getLogger("de.uniluebeck.itm.spitfire.gatewayconnectionmapper").setLevel(Level.DEBUG);

        //Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.communication.core").addAppender(new ConsoleAppender(new SimpleLayout()));
        Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.communication.core").setLevel(Level.DEBUG);

        Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.communication.encoding").setLevel(Level.DEBUG);

    }

    /**
     * @throws Exception might be everything
     */
    public static void main(String[] args) throws Exception {

        Configuration config = new PropertiesConfiguration("ssp.properties");

        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        ExecutionHandler executionHandler = new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(
                        config.getInt("threads", 30),
                        config.getLong("ram", 1024 * 1024),
                        config.getLong("ram", 1024 * 1024)));

        boolean enableVirtualHttpServerForCoap = config.getBoolean("coap.enableVirtualHttpServer", false);
        log.debug("Enable virtual HTTP server for CoAP devices: " + enableVirtualHttpServerForCoap);

        if(enableVirtualHttpServerForCoap){
            startConnectionMapper(config);
        }

        HttpEntityManagerPipelineFactory empf =
                new HttpEntityManagerPipelineFactory(executionHandler, enableVirtualHttpServerForCoap);
        bootstrap.setPipelineFactory(empf);
        int listenPort = config.getInt("listenPort", 8080);
        bootstrap.bind(new InetSocketAddress(listenPort));

        //Set URI base
        String defaultHost = InetAddress.getLocalHost().getCanonicalHostName();
        String baseURIHost = config.getString("baseURIHost", defaultHost);
        if(listenPort != 80){
            baseURIHost = baseURIHost + ":" + listenPort;
        }
        EntityManager.getInstance().setURIBase("http://" + baseURIHost);

        //Create enabled backends
        createBackends(config);
    }
    
    private static void startConnectionMapper(Configuration config) throws Exception{
        String udpNetworkInterfaceName = config.getString("udpInterfaceName");
        String tcpNetworkInterfaceName = config.getString("tcpInterfaceName");
        String tunInterfaceName = config.getString("tunInterfaceName");

        ConnectionMapper.start(udpNetworkInterfaceName, tcpNetworkInterfaceName, tunInterfaceName,
                5683, config.getInt("listenPort", 8080));
    }
    
//    private static void startConnectionMapper(Configuration config) throws URISyntaxException, SocketException {
//
//        File file = new File(Main.class.getResource("/libTUNWrapperCdl.so").toURI());
//        log.debug("File exists: " + file.exists());
//
//        //Get identifiers for UDP interface
//        NetworkInterface udpNetworkInterface = NetworkInterface.getByName(config.getString("udpInterfaceName"));
//        Inet6Address udpInterfaceGlobalIpv6 = getGlobalUniqueIpv6Address(udpNetworkInterface);
//        byte[] udpInterfaceHardwareAddress = udpNetworkInterface.getHardwareAddress();
//
//
//        //Get global IPv6 address for TCP interface
//        Inet6Address tcpInterfaceGlobalIpv6 = getGlobalUniqueIpv6Address(config.getString("tcpInterfaceName"));
//
//        //Get global IPv6 address for TUN interface
//        Inet6Address tunInterfaceGlobalIpv6 = getGlobalUniqueIpv6Address(config.getString("tunInterfaceName"));
//
//
//
//
//
//
//
////        try {
////            ConnectionMapper.start(file.getAbsolutePath(),
////                                   log,
////                                   config.getString("connectionMapper.tunBoundIP"),
////                    config.getInt("connectionMapper.localUdpServerPort"),
////                    config.getInt("listenPort"),
////                    config.getString("connectionMapper.tunUdpIP"),
////                    config.getString("connectionMapper.tunTcpIP"),
////                    config.getString("connectionMapper.udpNetIf"),
////                    config.getString("connectionMapper.udpNetIfMac"),
////                    config.getString("connectionMapper.tcpNetIf"),
////                    config.getString("connectionMapper.tcpNetIfMac"),
////                    config.getString("connectionMapper.tunNetIf"),
////                    addresses);
////        } catch (Exception e) {
////            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
////        }
//            
//
//        List<InetAddress> addresses = new ArrayList<InetAddress>();
//        
//        for(String s : config.getStringArray("connectionMapper.localBoundIP"))
//            try {
//                addresses.add(Inet6Address.getByName(s));
//            } catch (UnknownHostException e) {
//                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//
////        try {
////            ConnectionMapper.start(file.getAbsolutePath(),
////                                   log,
////                                   config.getString("connectionMapper.tunBoundIP"),
////                    config.getInt("connectionMapper.localUdpServerPort"),
////                    config.getInt("listenPort"),
////                    config.getString("connectionMapper.tunUdpIP"),
////                    config.getString("connectionMapper.tunTcpIP"),
////                    config.getString("connectionMapper.udpNetIf"),
////                    config.getString("connectionMapper.udpNetIfMac"),
////                    config.getString("connectionMapper.tcpNetIf"),
////                    config.getString("connectionMapper.tcpNetIfMac"),
////                    config.getString("connectionMapper.tunNetIf"),
////                    addresses);
////        } catch (Exception e) {
////            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
////        }
//    }
    

    
    //Create the backends enabled in ssp.properties 
    private static void createBackends(Configuration config) throws Exception {
        
        String[] enabledBackends = config.getStringArray("enableBackend");

        if(log.isDebugEnabled()){
            log.debug("[Main] Start creating enabled Backends!");
        }
        
        for(String enabledBackend: enabledBackends){

            Backend backend;

            //GeneratorBackend
            if(enabledBackend.equals("generator")) {

                backend = new GeneratorBackend(config.getInt("generator.nodes", 100),
                                               config.getInt("generator.features", 10),
                                               config.getDouble("generator.pValue", 0.5),
                                               config.getDouble("generator.pFeature", 0.01));
            }

            //SLSEBackend
            else if(enabledBackend.equals("slse")) {
                
                backend = new SLSEBackend(config.getBoolean("slse.waitForPolling", false),
                                          config.getBoolean("slse.parallelPolling", false),
										  config.getInt("slse.pollInterval", 10000)
										  );
                
                for(String proxy: config.getStringArray("slse.proxy")) {
                    ((SLSEBackend) backend).addProxy(proxy);
                }

                ((SLSEBackend) backend).pollProxiesForever();

            }

            //UberdustBackend
            else if(enabledBackend.equals("uberdust")) {

                backend = new UberdustBackend();

                for(String testbed: config.getStringArray("uberdust.testbed")) {
                    String[] tb = testbed.split(" ");
                    if(tb.length != 2) {
                        throw new Exception("Uberdust testbed has to be in the form 'http://server.tld:1234 5' " +
                                "(where 5 is the testbed-id)");
                    }
                    ((UberdustBackend) backend).addUberdustTestbed(tb[0], tb[1]);
                }
            }

            //WiselibTestBackend
            else if(enabledBackend.equals("wiselibtest")) {

               backend = new WiselibTestBackend();
            }

            //CoAPBackend
            else if(enabledBackend.startsWith("coap")) {
                String ipv6Prefix = config.getString(enabledBackend + ".ipv6Prefix");
                if(ipv6Prefix == null){
                    throw new Exception("Property '" + enabledBackend + ".ipv6Prefix' not set.");
                }
                backend = new CoapBackend(ipv6Prefix, config.getBoolean("coap.enableVirtualHttpServer", false));
                CoapNodeRegistrationServer.getInstance().addCoapBackend((CoapBackend) backend);
            }

            //UberdustCoapServerBackend
            else if(enabledBackend.equals("uberdustcoapserver")){
                String uberdustServerDnsName = config.getString("uberdustcoapserver.dnsName");
                if (uberdustServerDnsName == null){
                    throw new Exception("Property uberdustcoapserver.dnsName' not set.");
                }

                backend = new UberdustCoapServerBackend(uberdustServerDnsName, config);
                CoapNodeRegistrationServer.getInstance().addCoapBackend((UberdustCoapServerBackend) backend);
            }

            //SimpleBackend
            else if(enabledBackend.equals("simple")){

                backend = new SimpleBackend();
            }

            //FilesBackend
            else if(enabledBackend.equals("files")){
                String directory = config.getString("files.directory");
                if(directory == null){
                    throw new Exception("Property 'files.directory' not set.");
                }
                backend = new FilesBackend(directory);
            }

            //Unknown Backend Type
            else {
                throw new Exception("Config file error: Backend '" + enabledBackend + "' not found.");
            }

            backend.bind(EntityManager.getInstance());

            if(log.isDebugEnabled()){
                log.debug("[Main] Enabled new " + backend.getClass().getSimpleName() + " with path prefix " +
                    backend.getPathPrefix());
            }
        }
    }
    

    
	
}


