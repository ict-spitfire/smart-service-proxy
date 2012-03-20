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
package eu.spitfire_project.smart_service_proxy.backends.files;

import com.google.common.collect.HashMultimap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * @author Henning Hasemann
 */
public class FilesBackend extends Backend {
	private File baseDirectory = new File("data/files/");

    private HashMultimap<InetSocketAddress, String> observers = HashMultimap.create();

	@Override
	public void bind(EntityManager em) {
		super.bind(em);
		
		for(File file: baseDirectory.listFiles()) {
			if(!file.isFile()) { continue; }
			String filename = file.getName();
			if(filename.endsWith(".swp") || filename.startsWith(".")) { continue; }
			try {
				entityManager.entityCreated(new URI(getPathPrefix() + "/" + filename), this);
			}
			catch(java.net.URISyntaxException e) {
				e.printStackTrace();
			}
		} // for
	} // bind()

	
//	//@Override
//	public void getModel(URI uri_, final ChannelHandlerContext ctx, final boolean keepAlive) {
//		Model m = ModelFactory.createDefaultModel();
//
//		URI uri = entityManager.normalizeURI(uri_);
//		String f = baseDirectory + "/" + uri.getPath().substring(getPathPrefix().length());
//
//		try {
//			m.read(new FileInputStream(new File(f)), uri.toString(), "N3");
//		}
//		catch(java.io.FileNotFoundException e) {
//			e.printStackTrace();
//		}
//
//		ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, uri));
//		if(!keepAlive){
//			future.addListener(ChannelFutureListener.CLOSE);
//		}
//	}

    @Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!(e.getMessage() instanceof HttpRequest)){
            super.messageReceived(ctx, e);
        }

		HttpRequest request = (HttpRequest) e.getMessage();


        Model m = ModelFactory.createDefaultModel();

		URI uri = entityManager.normalizeURI(new URI(request.getUri()));
		String f = baseDirectory + "/" + uri.getPath().substring(getPathPrefix().length());

        boolean worked = false;
        while(!worked) {
            m.removeAll();
            try {
                m.read(new FileInputStream(new File(f)), uri.toString(), "N3");
                worked = true;
            }
            catch(java.io.FileNotFoundException ex) {
                ex.printStackTrace();
            }
            Thread.sleep(10);
        }

        m.removeAll();
        try {
            m.read(new FileInputStream(new File(f)), uri.toString(), "N3");
        }
        catch(java.io.FileNotFoundException ex) {
            ex.printStackTrace();
        }

		ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, uri));
		if(!HttpHeaders.isKeepAlive(request)){
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}
}

