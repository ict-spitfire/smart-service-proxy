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
import com.hp.hpl.jena.rdf.model.Statement;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.DynamicChannelBuffer;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 24.01.12
 * Time: 17:35
 * To change this template use File | Settings | File Templates.
 */
public class WiselibPacket {
	private byte[] inBytes;
	private Vector<Byte> outBytes;
	private int position;

	private static final byte STMT_FLAG_LITERAL_OBJ = 0x01;
	private static final byte STMT_FLAG_VARIABLE_SUBJ = 0x02;
	private static final byte STMT_FLAG_VARIABLE_PRED = 0x04;
	private static final byte STMT_FLAG_VARIABLE_OBJ = 0x08;

	public ChannelBuffer getChannelBuffer() {
		assert(outBytes.size() > 0);
		ChannelBuffer buffer = new DynamicChannelBuffer(outBytes.size());
		for(Byte b: outBytes) {
			buffer.writeByte(b);
		}
		assert(buffer.readableBytes() == outBytes.size());
		return buffer;
	}

	//
	// Readers
	//
	
	public interface Reader {
		public Object read();
	}

	public interface Writer {
		public void write(Object o);
	}

	public class StringReader implements Reader {
		@Override
		public Object read() {
			int end = position + inBytes[position];
			position++;
			String s = "";
			for(; position<=end; position++) {
				s += (char) inBytes[position];
			}
			return s;
		}
	}

	public class StringWriter implements Writer {
		@Override
		public void write(Object o) {
			String s = (String)o;
			(new ByteWriter()).write((byte) s.length());
			for(char c: s.toCharArray()) {
				outBytes.add((byte)c);
			}
		}
	}
	
	public class ListReader implements Reader {
		private Reader elementReader;
		public ListReader(Reader elementReader) {
			this.elementReader = elementReader;
		}
		@Override
		public Object read() {
			int size = inBytes[position];
			position++;
			List<Object> r = new LinkedList<Object>();
			for(int i = 0; i<size; i++) {
				r.add(elementReader.read());
			}
			return r;
		}
	}
	
	public class ListWriter implements Writer {
		private Writer elementWriter;
		public ListWriter(Writer elementWriter) {
			this.elementWriter = elementWriter;
		}
		@Override
		public void write(Object o) {
			List l = (List)o;
			(new ByteWriter()).write(l.size());
			for(Object element: l) {
				elementWriter.write(element);
			}
		}
	}
	
	public class StatementReader implements Reader {
		private Model model;
		private boolean add;

		public StatementReader(Model m, boolean add) {
			this.model = m;
			this.add = add;
		}
		
		@Override
		public Object read() {
			byte flags = inBytes[position++];
			Boolean literalObject = (flags & STMT_FLAG_LITERAL_OBJ) != 0;
			// ignore variable flags, assume sensors don't send queries to us
			String subject = (String)new StringReader().read();
			String property = (String)new StringReader().read();
			String object = (String)new StringReader().read();
			
			Statement st = model.createStatement(
					model.createResource(subject),
					model.createProperty(property),
					literalObject ? model.createLiteral(object) : model.createResource(object)
			);
			if(add) {
				model.add(st);
			}
			return st;
		}
	}
	
	public class StatementWriter implements Writer {
		@Override
		public void write(Object o) {
			Statement st = (Statement)o;
			byte flags = 0;
			if(st.getObject().isLiteral()) {
				flags |= STMT_FLAG_LITERAL_OBJ;
			}
			(new ByteWriter()).write(flags);
			StringWriter sw = new StringWriter();
			sw.write(st.getSubject().getURI());
			sw.write(st.getPredicate().getURI());
			if(st.getObject().isLiteral()) {
				sw.write(st.getObject().asLiteral().getString());
			}
			else {
				sw.write(st.getObject().asResource().getURI());
			}
		}
	}

	public class SimpleQueryStatementWriter implements Writer {
		@Override
		public void write(Object o) {
			SimpleQueryStatement sqs = (SimpleQueryStatement)o;
			byte flags = 0;
			if(sqs.isObjectLiteral()) {
				flags |= STMT_FLAG_LITERAL_OBJ;
			}
			if(sqs.isSubjectVariable()) {
				flags |= STMT_FLAG_VARIABLE_SUBJ;
			}
			if(sqs.isPredicateVariable()) {
				flags |= STMT_FLAG_VARIABLE_PRED;
			}
			if(sqs.isObjectVariable()) {
				flags |= STMT_FLAG_VARIABLE_OBJ;
			}
			(new ByteWriter()).write(flags);
			StringWriter sw = new StringWriter();
			sw.write(sqs.getSubject());
			sw.write(sqs.getPredicate());
			sw.write(sqs.getObject());
		}
	}
	
	public class SimpleQueryWriter implements Writer {
		@Override
		public void write(Object o) {
			SimpleQuery query = (SimpleQuery)o;
			SimpleQueryStatementWriter sqsWriter = new SimpleQueryStatementWriter();
			(new ByteWriter()).write((byte)query.getStatements().size());
			for(SimpleQueryStatement sqs: query.getStatements()) {
				sqsWriter.write(sqs);
			}
		}
	}

	public class ByteReader implements Reader {
		@Override
		public Object read() {
			return (Object) inBytes[position++];
		}
	}
	public class ByteWriter implements Writer {
		@Override
		public void write(Object o) {
			outBytes.add((Byte)o);
		}
	}

	public static WiselibPacket readFrom(byte[] inBytes) {
		WiselibPacket p = new WiselibPacket();
		p.inBytes = inBytes;
		p.position = 2;
		return p;
	}
	
	public static WiselibPacket create(byte type, byte subtype) {
		WiselibPacket p = new WiselibPacket();
		p.outBytes = new Vector<Byte>();
		p.outBytes.add(type);
		p.outBytes.add(subtype);
		return p;
	}

	private WiselibPacket() {

	}
/*
	public WiselibPacket(byte[] inBytes) {
		this.inBytes = inBytes;
		this.outBytes = new Vector<Byte>();
		this.position = 2;
	}
*/
	public byte getSubType() {
		return inBytes[1];
	}

	public void rewind() {
		position = 2;
	}

}
