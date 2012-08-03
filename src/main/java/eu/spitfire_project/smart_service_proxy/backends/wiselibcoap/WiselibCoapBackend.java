package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import com.google.common.collect.HashMultimap;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibProtocol;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 06.07.12
 * Time: 16:38
 * To change this template use File | Settings | File Templates.
 */
public class WiselibCoapBackend extends Backend implements CoapListener{

    private HashMultimap<InetSocketAddress, String> observers = HashMultimap.create();
	private Map<String, String> translations = new HashMap<String, String>();
    private DirectConnect connector;


    private class SE {
		public Model model;
		public long lastUpdate;
		public String uri;
	}

	Map<String, SE> seCache = new ConcurrentHashMap<String, SE>();

	public WiselibCoapBackend() {
		Timer t = new Timer();
		t.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkOnSEs();
			}
		}, 1000, 10000);

		translations.put("ex:", "http://example.org/#");
        connector = new DirectConnect("/dev/tty.usbserial-000013FD");
        connector.getCoapMessageHandler().addListener(this);
        connector.requestEnteties();
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

	Vector<Vector<Byte>> deHex(String[] stringbytes) {
		Vector<Byte> r = new Vector<Byte>();
		for(int i=0; i<stringbytes.length; i++) {
			//System.out.println(stringbytes[i]);
			int v = Integer.decode(stringbytes[i]);
			r.add((byte)(v <= 127 ? v : -128 - v)); // seriously java, fuck off.
		}
		Vector< Vector<Byte> > rr = new Vector< Vector<Byte> >();
		rr.add(r);
		return rr;
	}

    @Override
    public void onSemanticDescription(WiselibProtocol.SemanticEntity se) {
        createSEFromDescription(se);
    }

	String normalize(String u) {
        if(u.startsWith("<"))
            u=u.substring(1,u.length()-2);
		URI base = URI.create("http://spitfire-project.eu/spitfireCC.n3#");
		return base.resolve(URI.create(u)).toString();
	}

	private void createSEFromDescription(WiselibProtocol.SemanticEntity se_desc) {
        //TODO have nodeId in se_desc?
		if(se_desc.getId().equals("")) { return; }

		URI uri = entityManager.normalizeURI(getPathPrefix() + se_desc.getId());
		Model m = ModelFactory.createDefaultModel();
		for(WiselibProtocol.Statement statement: se_desc.getDescription().getStatementList()) {
			System.out.println("(" + statement.getSubject() + " " + statement.getPredicate() + " " + statement.getObject() + ")");


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
