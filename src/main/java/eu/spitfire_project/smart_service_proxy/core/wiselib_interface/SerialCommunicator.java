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
package eu.spitfire_project.smart_service_proxy.core.wiselib_interface;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.uniluebeck.itm.netty.handlerstack.dlestxetx.DleStxEtxFramingDecoder;
import de.uniluebeck.itm.wsn.drivers.core.Device;
import de.uniluebeck.itm.wsn.drivers.factories.DeviceFactory;
import de.uniluebeck.itm.wsn.drivers.factories.DeviceFactoryImpl;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.iostream.IOStreamAddress;
import org.jboss.netty.channel.iostream.IOStreamChannelFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 24.01.12
 * Time: 16:09
 * To change this template use File | Settings | File Templates.
 */
public class SerialCommunicator {
	private Device device;
	
	SerialCommunicator(String port, String deviceType) {
		//final Injector injector = Guice.createInjector(new DeviceUtilsModule());
		final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("DeviceConnector %d").build();
		final ExecutorService executorService = Executors.newCachedThreadPool(threadFactory);
		Map<String, String> configuration = new HashMap<String, String>();
		final DeviceFactory factory = new DeviceFactoryImpl();
		Device device = factory.create(executorService, deviceType, null);
		try {
			device.connect(port);
		} catch (IOException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
		
		final InputStream inputStream = device.getInputStream();
		final OutputStream outputStream = device.getOutputStream();
		final ClientBootstrap bootstrap = new ClientBootstrap(new IOStreamChannelFactory(executorService));

		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
				pipeline.addLast("loggingHandler", new SimpleChannelHandler() {
					@Override
					public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
							throws Exception {
						final ChannelBuffer message = (ChannelBuffer) e.getMessage();
						byte[] messageBytes = new byte[message.readableBytes()];
						message.readBytes(messageBytes);
						//finalOutWriter.write(messageBytes);
						//messageBytes
					}
				});

				pipeline.addLast("DleStxDecoder", new DleStxEtxFramingDecoder());
				pipeline.addLast("DleStxEncoder", new DleStxEtxFramingDecoder());
// TODO
				return pipeline;
			}
		}
		);

		// Make a new connection.
		ChannelFuture connectFuture = bootstrap.connect(new IOStreamAddress(inputStream, outputStream));

		// Wait until the connection is made successfully.
		connectFuture.awaitUninterruptibly().getChannel();


		//injector.getInstance(DeviceFactory.class).create(executorService, deviceType, configuration);
	}
}
