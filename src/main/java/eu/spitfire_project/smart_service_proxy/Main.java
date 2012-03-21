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

import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
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
import org.apache.log4j.*;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {

    private static Logger log = Logger.getLogger(Main.class.getName());
    
    static{
        Logger.getLogger("eu.spitfire_project.smart_service_proxy").addAppender(new ConsoleAppender(new SimpleLayout()));
        Logger.getLogger("eu.spitfire_project.smart_service_proxy").setLevel(Level.DEBUG);
    }

    private static void createBackends(Configuration config) throws Exception {
        
        String[] enabledBackends = config.getStringArray("enableBackend");

        if(log.isDebugEnabled()){
            log.debug("[Main] Instanciating Backends!");    
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
                                          config.getBoolean("slse.parallelPolling", false));
                
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
            else if(enabledBackend.equals("coap")) {

                String ipv6Prefix = config.getString("coap.ipv6Prefix");
                if(ipv6Prefix == null){
                    throw new Exception("Propertiy 'coap.ipv6Prefix' not set.");
                }
                backend = new CoapBackend(ipv6Prefix);
            }

            //SimpleBackend
            else if(enabledBackend.equals("simple")){

                backend = new SimpleBackend();
            }

            //FilesBackend
            else if(enabledBackend.equals("files")){
                String directory = config.getString("files.directory");
                if(directory == null){
                    throw new Exception("Propertiy 'files.directory' not set.");
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

    /**
     * @param args [waitForPolling] [pollParallel]
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

        HttpEntityManagerPipelineFactory empf = new HttpEntityManagerPipelineFactory(executionHandler);
        bootstrap.setPipelineFactory(empf);
        bootstrap.bind(new InetSocketAddress(config.getInt("listenPort", 8080)));
		
		String uriBase = config.getString("uriBase", "auto");
		if(!"auto".equals(uriBase)) {
			EntityManager.getInstance().setURIBase(uriBase);
		}
        
        createBackends(config);

//        System.out.println("# begin_config_dump");
//        Iterator iter = config.getKeys();
//        while(iter.hasNext()) {
//            String key = (String)iter.next();
//            System.out.println("# " + key + " = " + config.getString(key));
//        }
//        System.out.println("# end_config_dump");
//
//		System.out.println(System.currentTimeMillis() + " boot");
	}
	
}


