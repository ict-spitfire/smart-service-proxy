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

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 24.01.12
 * Time: 17:16
 * To change this template use File | Settings | File Templates.
 */
public class Translator extends SimpleChannelHandler {
	private enum MessageTypes {
		SE_CREATED(1),
		SE_DESTROYED(2),
		SE_DESCRIPTION(3),
		SE_DATA(4),
		SE_ADD_RULE(5),
		SE_POLL(6),
		SE_QUERY(7),
		SE_QUERY_RESPONSE(8),
		SE_ADD_TASK(9),
		SE_TASK_RESPONSE(10),
		SE_REMOVE_TASK(11),
		SE_MESSAGE(106);

		private final byte id;
		MessageTypes(int i) {
			this.id = (byte)i;
		}
	}

	private Communicator communicator;
	private Set<WiselibListener> listeners;

	public Translator(Communicator communicator) {
		listeners = new HashSet<WiselibListener>();
		this.communicator = communicator;
		this.communicator.bind(this);
	}
	
	public void registerListener(WiselibListener listener) {
		listeners.add(listener);
	}

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
			throws Exception {
		final ChannelBuffer message = (ChannelBuffer) e.getMessage();
		byte[] messageBytes = new byte[message.readableBytes()];
		
		message.readBytes(messageBytes);

		if(messageBytes.length < 2 || (messageBytes[0] != MessageTypes.SE_MESSAGE.id)) {
			System.out.println("Message ignored. length=" + messageBytes.length);
			if(messageBytes.length > 0) { System.out.println(" type=" + (int)messageBytes[0]); }
			super.messageReceived(ctx, e);
			return;
		}

		WiselibPacket packet = WiselibPacket.readFrom(messageBytes);

		byte t = packet.getSubType();

		if(t == MessageTypes.SE_CREATED.id) {
			entityCreated(packet);
		}
		else if(t == MessageTypes.SE_DESTROYED.id) {
			entityDestroyed(packet);
		}
		else if(t == MessageTypes.SE_DESCRIPTION.id) {
			entityDescription(packet);
		}
		else if(t == MessageTypes.SE_DATA.id) {
			entityData(packet);
		}
	}

	private void entityData(WiselibPacket packet) {
		String id = (String)packet.new StringReader().read();
		Model model = ModelFactory.createDefaultModel();
		WiselibPacket.StatementReader statementReader = packet.new StatementReader(model, true);
		packet.new ListReader(statementReader).read();
		for(WiselibListener listener: listeners) {
			listener.entityData(id, model);
		}
	}

	private void entityDescription(WiselibPacket packet) {
		String id = (String)packet.new StringReader().read();
		Model model = ModelFactory.createDefaultModel();
		WiselibPacket.StatementReader statementReader = packet.new StatementReader(model, true);
		packet.new ListReader(statementReader).read();
		for(WiselibListener listener: listeners) {
			listener.entityDescription(id, model);
		}
	}

	private void entityDestroyed(WiselibPacket packet) {
		String id = (String)packet.new StringReader().read();
		int reason = (Byte)packet.new ByteReader().read();
		for(WiselibListener listener: listeners) {
			listener.entityDestroyed(id, reason);
		}
	}

	private void entityCreated(WiselibPacket packet) {
		String id = (String)packet.new StringReader().read();
		for(WiselibListener listener: listeners) {
			listener.entityCreated(id);
		}
	}

	public void issueQuery(String id, SimpleQuery q) {
		WiselibPacket p = WiselibPacket.create(MessageTypes.SE_MESSAGE.id, MessageTypes.SE_QUERY.id);
		(p.new StringWriter()).write(id);
		(p.new SimpleQueryWriter()).write(q);
		communicator.send(p);
	}

}
