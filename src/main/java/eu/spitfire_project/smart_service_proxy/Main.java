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

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import de.uniluebeck.itm.spitfire.gatewayconnectionmapper.ConnectionMapper;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import eu.spitfire_project.smart_service_proxy.backends.coap.uberdust.UberdustCoapServerBackend;
import eu.spitfire_project.smart_service_proxy.backends.files.FilesBackend;
import eu.spitfire_project.smart_service_proxy.backends.generator.GeneratorBackend;
import eu.spitfire_project.smart_service_proxy.backends.simple.SimpleBackend;
import eu.spitfire_project.smart_service_proxy.backends.slse.SLSEBackend;
import eu.spitfire_project.smart_service_proxy.backends.uberdust.UberdustBackend;
import eu.spitfire_project.smart_service_proxy.backends.wiselib_test.WiselibTestBackend;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.ShdtSerializer;
import eu.spitfire_project.smart_service_proxy.core.httpServer.HttpEntityManagerPipelineFactory;
import eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration.annotation.AutoAnnotation;
import eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration.CoapNodeRegistrationServer;
import eu.spitfire_project.smart_service_proxy.visualization.VisualizerClient;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    private static Logger log = Logger.getLogger(Main.class.getName());

    public static Channel httpChannel;

    static{
        String pattern = "%-23d{yyyy-MM-dd HH:mm:ss,SSS} | %-32.32t | %-30.30c{1} | %-5p | %m%n";
        PatternLayout patternLayout = new PatternLayout(pattern);

        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new ConsoleAppender(patternLayout));

        Logger.getRootLogger().setLevel(Level.ERROR);
        Logger.getLogger("eu.spitfire_project.smart_service_proxy").setLevel(Level.DEBUG);
        Logger.getLogger("eu.spitfire_project.smart_service_proxy.core.ShdtSerializer").setLevel(Level.ERROR);
        Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.communication").setLevel(Level.ERROR);
    }

	private static void testShdt() {
		Model m = ModelFactory.createDefaultModel();
		//m.read("http://dbpedia.neofonie.de/browse/rdf-type:River/River-mouth:Rhine/Place-length~:50000~/?fc=30");
		m.read("http://spitfire.ibr.cs.tu-bs.de/be-0001/b4ec27c5-d543-496a-b2bf-a960134dcb37/2/sensor#");
		ShdtSerializer shdt = new ShdtSerializer(128);
		byte[] buffer = new byte[10 * 1024];
		int l = shdt.fill_buffer(buffer, m.listStatements());
		for(int i=l; i<buffer.length; i++) { buffer[i] = (byte) 0xff; }

		//System.out.println(Arrays.toString(buffer));
		//System.out.println(new String(buffer));

		shdt.reset();

		Model m2 = ModelFactory.createDefaultModel();
		shdt.read_buffer(m2, buffer);
		System.out.println(m2.toString());
		System.out.println("equal: " + m.isIsomorphicWith(m2));
		StmtIterator iter = m2.listStatements();
		while(true) {
			Statement st = iter.nextStatement();
			System.out.println(st);
		}
	}

    /**
     * @throws Exception might be everything
     */
    public static void main(String[] args) throws Exception {
		//testShdt();

        Configuration config = new PropertiesConfiguration("ssp.properties");

        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));



        boolean enableVirtualHttpServerForCoap = config.getBoolean("coap.enableVirtualHttpServer", false);
        log.debug("Enable virtual HTTP server for CoAP devices: " + enableVirtualHttpServerForCoap);

        if(enableVirtualHttpServerForCoap){
            startConnectionMapper(config);
        }

        HttpEntityManagerPipelineFactory empf =
                new HttpEntityManagerPipelineFactory(enableVirtualHttpServerForCoap);
        bootstrap.setPipelineFactory(empf);
        int listenPort = config.getInt("SSP_HTTP_SERVER_PORT", 8080);
        httpChannel = bootstrap.bind(new InetSocketAddress(listenPort));
        log.info("HTTP server started. Listening on port " + listenPort);

        //Set URI base
        String defaultHost = InetAddress.getLocalHost().getCanonicalHostName();
        String baseURIHost = config.getString("SSP_DNS_NAME", defaultHost);
        if(listenPort != 80){
            baseURIHost = baseURIHost + ":" + listenPort;
        }

        //Create enabled backends
        createBackends(config);

        //Set AutoAnnotation to use images from visualizer
        //AutoAnnotation.getInstance().setVisualizerClient(VisualizerClient.getInstance());
        //AutoAnnotation.getInstance().start();
        //new SimulatedTimeScheduler().run();
    }
    
    private static void startConnectionMapper(Configuration config) throws Exception{
        String udpNetworkInterfaceName = config.getString("udpInterfaceName");
        String tcpNetworkInterfaceName = config.getString("tcpInterfaceName");
        String tunInterfaceName = config.getString("tunInterfaceName");

        ConnectionMapper.start(udpNetworkInterfaceName, tcpNetworkInterfaceName, tunInterfaceName,
                5683, config.getInt("SSP_HTTP_SERVER_PORT", 8080));
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

            //SLSEBackendsrc/main/java/eu/spitfire_project/smart_service_proxy/backends/slse
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

                String ipv6Prefix = Inet6Address.getByName(config.getString(enabledBackend + ".ipv6Prefix"))
                                                .getHostAddress();

                ipv6Prefix = ipv6Prefix.substring(0, ipv6Prefix.indexOf(":0:0:0:0"));

                backend = new CoapBackend(ipv6Prefix,
                                          config.getBoolean(enabledBackend + ".enableVirtualHttpServer", false));

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

            backend.bind();

            log.debug("Enabled new " + backend.getClass().getSimpleName() + " with prefix " +
                    backend.getPrefix());
        }

//        CoapNodeRegistrationServer.getInstance()
//                                  .fakeRegistration(InetAddress.getByName("[2001:db08:0:c0a1:215:8d00:11:a88]"));

//        CoapNodeRegistrationServer.getInstance()
//                .fakeRegistration(InetAddress.getByName("[2001:db08:0:c0a1:215:8d00:14:8e82]"));
    }
    

    
	
}


