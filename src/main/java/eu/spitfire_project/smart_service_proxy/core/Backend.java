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
package eu.spitfire_project.smart_service_proxy.core;

import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import org.jboss.netty.channel.*;

import java.net.URI;
import java.util.*;


/**
 * Base class for all backends.
 * 
 * @author Henning Hasemann
 * @author Oliver Kleine
 */
public abstract class Backend extends SimpleChannelHandler {

    //protected EntityManager entityManager;
	protected String prefix;

    /**
     * Binds the {@link Backend} to an {@link eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager} which means, that this EntityManager gets known
     * of all resources (i.e. their URIs} provided by the Backend
     */
	public void bind() {
		EntityManager.getInstance().registerBackend(this);
		//entityManager = em;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception{
        super.messageReceived(ctx, e);
    }
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		super.writeRequested(ctx, e);
	}
    
//    public EntityManager getEntityManager() {
//        return entityManager;
//    }

	/**
	 * Set base prefix (in URI) for this backend. If prefix is e.g. be-0001, the backend is reachable
     * at http://<IP address of SSP>/be-001)
     * @param prefix The prefix prefix the Backend shall be reachable at
	 */
	public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

	/**
	 * Get base prefix (in URI) for this backend.
     * @return the prefix part of the URI where the Backend is reachable at
	 */
	public String getPrefix() {
        return prefix;
    }
	
	/**
	 * Return entity description by URI.
	 */
	//public abstract void getModel(URI uri, /*final ChannelHandlerContext ctx, */final boolean keepAlive);
	
	/**
	 * Return list of user interface elements.
	 */
	public Collection<UIElement> getUIElements() {
		return new Vector<UIElement>();
	}
    
    public Set<URI> getResources(){
        return new HashSet<URI>();
    }

    @Override
    public String toString(){
        return "{" + this.getClass().getName() + " for prefix: " + getPrefix() + "}";
    }

}

