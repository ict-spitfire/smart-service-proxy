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
package eu.spitfire_project.smart_service_proxy.backends.wisebed;

import com.google.common.collect.HashMultimap;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibProtocol;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import java.util.concurrent.*;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

/**
 * @author Henning Hasemann
 */
public class TestbedBackend extends Backend implements TailerListener {
    private HashMultimap<InetSocketAddress, String> observers = HashMultimap.create();
	private Tailer tailer;
	private Map<String, String> translations = new HashMap<String, String>();

	private class SE {
		public Model model;
		public long lastUpdate;
		public String uri;
	}
	
	Map<String, SE> seCache = new ConcurrentHashMap<String, SE>();
	
	public TestbedBackend(String path) {
		tailer = Tailer.create(new File(path), this);
		Timer t = new Timer();
		t.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkOnSEs();
			}
		}, 1000, 10000);
		
		translations.put("ex:", "http://example.org/#");
	}
	
	public void checkOnSEs() {
		//System.out.println("check on ses");
		// TODO: check which SEs are timed out
		for(Map.Entry<String, SE> seEntry: seCache.entrySet()) {
			//Calendar out = seEntry.getValue().lastUpdate;
			if(seEntry.getValue().lastUpdate + 10000 < System.currentTimeMillis()) {
				entityManager.entityDestroyed(seEntry.getKey());
				seCache.remove(seEntry.getKey());
			}
		}
	}

	@Override
	public void bind(EntityManager em) {
		super.bind(em);
	} // bind()

	
	public void open(String path) {
		tailer = Tailer.create(new File(path), this);
	}
	
	
	public void init(Tailer tailer) {
	}
	
	public void fileNotFound() {
	}
	
	public void fileRotated() {
	}
	
	public void handle(Exception ex) {
		ex.printStackTrace();
	}
	
	
	Vector< Vector<Byte> > deHex(String[] stringbytes) {
		Vector<Byte> r = new Vector<Byte>();
		for(int i=0; i<stringbytes.length; i++) {
			System.out.println(stringbytes[i]);
			int v = Integer.decode(stringbytes[i]);
			r.add((byte)(v <= 127 ? v : -128 - v)); // seriously java, fuck off.
		}
		Vector< Vector<Byte> > rr = new Vector< Vector<Byte> >();
		rr.add(r);
		return rr;
	}
	
	Vector< Vector<Byte> > decodeDLE(Vector<Byte> in) {
		boolean escaped = false;
		Byte dle = 0x10, stx = 0x02, etx = 0x03;
		Vector< Vector<Byte> > r = new Vector< Vector<Byte> >();
		Vector<Byte> currentPacket = new Vector<Byte>();
		
		for(Byte b: in) {
			if(escaped) {
				if(b.equals(stx)) {
				}
				else if(b.equals(etx)) {
					r.add(currentPacket);
					currentPacket = new Vector<Byte>();
				}
				else if(b.equals(dle)) {
					currentPacket.add(b);
				}
				else {
					assert(false);
				}
				escaped = false;
			}
			else {
				if(b.equals(dle)) {
					escaped = true;
				}
				else {
					currentPacket.add(b);
				}
			}
		}
		
		return r;
	}
	
	Vector< Vector<Byte> > filterType(byte type, Vector< Vector<Byte> > in) {
		Vector< Vector<Byte> > r = new Vector< Vector<Byte> >();
		for(Vector<Byte> v: in) {
			//System.out.println("filterType " + v.get(0) + " " + v.get(1) + " " + v.get(2));
			if(v.size() >= 3 && v.get(1) == type) {
				r.add(v);
			}
		}
		return r;
	}
	
	public void handle(String line) {
		System.out.println("reading line: >>>" + line + "<<<");

		int pos = line.indexOf('|');
		pos = line.indexOf('|', pos+1);
		pos = line.indexOf('|', pos+1);
		
		String[] stringbytes = line.substring(pos+1).trim().split(" ");
		
		//for(Vector<Byte> v: filterType((byte)'X', decodeDLE(deHex(stringbytes)))) {
		for(Vector<Byte> v: filterType((byte)'X', deHex(stringbytes))) {
			//byte[] bytes = v.toArray();
			System.out.println("analysing msg of len " + v.size());
			
			byte[] fuckYouJava = new byte[v.size()];
			for(int i=0; i<v.size(); i++) {
				fuckYouJava[i] = v.get(i);
			}
		
			try {
				WiselibProtocol.SemanticEntity se = WiselibProtocol.SemanticEntity.parseFrom(fuckYouJava);
				System.out.println(se.toString());
				createSEFromDescription(se);
				
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		
	}

	String normalize(String u) {
		URI base = URI.create("http://spitfire-project.eu/spitfireCC.n3#");
		return base.resolve(URI.create(u)).toString();
	}

	private void createSEFromDescription(WiselibProtocol.SemanticEntity se_desc) {
		URI uri = entityManager.normalizeURI(getPathPrefix() + se_desc.getId());
		Model m = ModelFactory.createDefaultModel();
		for(WiselibProtocol.Statement statement: se_desc.getDescription().getStatementList()) {
			Resource s = m.createResource(normalize(statement.getSubject()));
			Property p = m.createProperty(normalize(statement.getPredicate()));
			String obj = statement.getObject();
			RDFNode o = obj.startsWith("\"") ? m.createLiteral(obj.substring(1)) : m.createResource(normalize(obj));

			m.add(m.createStatement(s, p, o));
		}
		SE s = new SE();
		s.uri = uri.toString();
		s.model = m;
		s.lastUpdate = System.currentTimeMillis();
		seCache.put(s.uri, s);
		entityManager.entityCreated(URI.create(s.uri), this);
	}
	
	//public void reserveTestbed() {
		
	
    @Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!(e.getMessage() instanceof HttpRequest)){
            super.messageReceived(ctx, e);
        }

		HttpRequest request = (HttpRequest) e.getMessage();


        //Model m = ModelFactory.createDefaultModel();

		URI uri = entityManager.normalizeURI(new URI(request.getUri()));

		Model m = seCache.get(uri.toString()).model;
		
		
		/*
        m.removeAll();
        try {
            m.read(new FileInputStream(new File(f)), uri.toString(), "N3");
        }
        catch(java.io.FileNotFoundException ex) {
            ex.printStackTrace();
        }
		*/

		ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, uri));
		if(!HttpHeaders.isKeepAlive(request)){
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}
}

