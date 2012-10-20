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
package eu.spitfire_project.smart_service_proxy.backends.wiselib_test;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.MockCommunicator;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.SimpleQuery;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.Translator;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibListener;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;

/**
 * @author Henning Hasemann
 */
public class WiselibTestBackend extends Backend implements WiselibListener {

	Translator translator;

	public WiselibTestBackend() {
		byte dle = 0x10, stx = 0x02, etx = 0x03;
		byte[] input = {
				// Entity created
				dle, stx, 106, 0x01, 0x05, 'H', 'e', 'l', 'l', 'o', dle, etx,

				// Entity description update
				dle, stx,
				106, 0x03, 0x05, 'H', 'e', 'l', 'l', 'o', 1, 0, // 1 statements, flags=0
				10, 'h', 't', 't', 'p', ':', '/', '/', 'f', 'o', 'o',
				10, 'h', 't', 't', 'p', ':', '/', '/', 'b', 'a', 'r',
				3, 'b', 'a', 'z',
				dle, etx,

				// Entity value update
				dle, stx,
				106, 0x04, 0x05, 'H', 'e', 'l', 'l', 'o', 1, 1, // 1 statements, flags=0
				10, 'h', 't', 't', 'p', ':', '/', '/', 'f', 'o', 'o',
				10, 'h', 't', 't', 'p', ':', '/', '/', 'b', 'a', 'r',
				3, '1', '2', '3',
				dle, etx,

				// Entity destroyed
				dle, stx, 106, 0x02, 0x05, 'H', 'e', 'l', 'l', 'o', 42, dle, etx,
		};
		
		ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		MockCommunicator communicator = new MockCommunicator(inputStream,outputStream);
		translator = new Translator(communicator);
		translator.registerListener(this);

		// Issue out a query

		SimpleQuery query = new SimpleQuery();
		query.addStatement("?node", "ssn:attachedSystem", "?sensor", false);
		query.addStatement("?sensor", "dul:haslocation", "http://dbpedia.org/resource/Braunschweig", false);

		/*
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
		*/

		// Note that the following code might crash if reading from the input stream is too fast and
		// thus the channel gets closed before the write

		translator.issueQuery("myQuery", query);

		for(byte b: outputStream.toByteArray()) {
			System.out.println(String.format("%c 0x%x",b,b));
		}
	}

	@Override
	public void bind() {
		super.bind();
		
	} // bind()

	
    @Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!(e.getMessage() instanceof HttpRequest)){
            super.messageReceived(ctx, e);
        }
		HttpRequest request = (HttpRequest) e.getMessage();

        Model m = ModelFactory.createDefaultModel();

		URI uri = EntityManager.getInstance().normalizeURI(new URI(request.getUri()));




		ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, uri));
		if(!HttpHeaders.isKeepAlive(request)){
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void entityCreated(String id) {
		System.out.println("Entity created: " + id);
	}

	@Override
	public void entityDestroyed(String id, int reason) {
		System.out.println("Entity destroyed: " + id + " reason: " + reason);
	}

	@Override
	public void entityDescription(String id, Model model) {
		System.out.println("id=" + id);
		System.out.println("description=" + model);
	}

	@Override
	public void entityData(String id, Model model) {
		System.out.println("id=" + id);
		System.out.println("data=" + model);
	}
}

