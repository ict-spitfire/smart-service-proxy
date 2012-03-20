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

import eu.spitfire_project.smart_service_proxy.core.observing.HttpPipelineFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Executors;


/**
 * Base class for all backends.
 * 
 * @author Henning Hasemann
 * @author Oliver Kleine
 */
public abstract class Backend extends SimpleChannelHandler {

    private static ClientBootstrap clientBootstrap =
        new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                                                              Executors.newCachedThreadPool()));
    static{
        clientBootstrap.setPipelineFactory(new HttpPipelineFactory());
    }

    private Thread observationThread;
    private Hashtable<String, ObservedEntity> observedEntities = new Hashtable<String, ObservedEntity>();
    private Hashtable<String, EntityObserver> pendingObservations = new Hashtable<String, EntityObserver>();

    protected EntityManager entityManager;
	protected String pathPrefix;


    //Protected constructor starts one background thread per instance to handle observations
    protected Backend(){
        observationThread = new Thread(){
            @Override
            public void run(){
                while(true){
                    //Notify all entityObserver observing the observedEntity
                    for(String entityPath : observedEntities.keySet()){
                        ObservedEntity observedEntity = observedEntities.get(entityPath);
                        observedEntity.notifyObservers(entityPath);
                    }

                    //Send HTTP messages to entityObservers observing service
                    for(String entityPath : pendingObservations.keySet()){
                        EntityObserver entityObserver = pendingObservations.get(entityPath);
                        if(System.currentTimeMillis() - entityObserver.lastTimeUpdateSent > entityObserver.interval){
                            String msg = "[MockUp] Send HTTP request for path " + entityPath +
                            "to " + entityObserver.observerAddress + "!";
                            System.out.println("[MockUp] Send HTTP Request for ");
                            pendingObservations.remove(entityPath);
                         }
                    }
                    System.out.println("[" + Thread.currentThread() +"] finished a circle!");
                }
            }
        };

        //observationThread.start();
    }
    /**
     * Binds the {@link Backend} to an {@link EntityManager} which means, that this EntityManager gets known
     * of all resources (i.e. their URIs} provided by the Backend
     * @param em The EntityManager instance the Backend should be bound to
     */
	public void bind(EntityManager em) {
		em.registerBackend(this);
		entityManager = em;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!(e.getMessage() instanceof HttpRequest)){
            super.messageReceived(ctx, e);
        }
	}
	
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		super.writeRequested(ctx, e);
	}

//    public void respond(MessageEvent e) {
//		ChannelFuture future = e.getChannel().write(this);
//		HttpRequest request = (HttpRequest) e.getMessage();
//    	future.addListener(ChannelFutureListener.CLOSE);
//	}

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

	/**
	 * Set base pathPrefix (in URI) for this backend. If pathPrefix is e.g. be-0001, the backend is reachable
     * at http://<IP address of SSP>/be-001)
     * @param pathPrefix The pathPrefix prefix the Backend shall be reachable at
	 */
	public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

	/**
	 * Get base pathPrefix (in URI) for this backend.
     * @return the pathPrefix part of the URI where the Backend is reachable at
	 */
	public String getPathPrefix() {
        return pathPrefix;
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

    /**
     * Sends an HttpResponse message to the client
     * @param ctx The ChannelHandlerContext of the handler sending the response
     * @param request The HttpRequest to respond to
     * @param status The reponse status
     */
    public void sendHttpResponse(ChannelHandlerContext ctx, HttpMessage request, HttpResponseStatus status){

        ChannelFuture future = Channels.write(ctx.getChannel(),
                new DefaultHttpResponse(request.getProtocolVersion(), status));
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public void addObserving(ChannelHandlerContext ctx, MessageEvent e){
        HttpRequest request = (HttpRequest) e.getMessage();
        System.out.println("URI in FilesBackend: " + request.getUri());
        try{
            URI targetURI = new URI(request.getUri());

            //try to extract query part (there is only one parameter 'observeInterval' allowed
            String query = targetURI.getQuery();
            if(query == null || query.split("&").length > 1 || !query.split("=").equals("observeInterval")){
                sendHttpResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            //Now its for sure that there is only one query parameter "observeInterval"
            String value = query.split("=")[1];
            System.out.println("Value = " + value);
            try{
                int interval = Integer.valueOf(value);

                //get the proper observed entity or create a new one
                ObservedEntity observedEntity = observedEntities.get(targetURI.getPath());
                if(observedEntity == null){
                    observedEntity = new ObservedEntity(targetURI.getPath());
                }

                //create the new entity observer and add it as observer to the ebserved entity
                EntityObserver entityObserver =
                        new EntityObserver(((InetSocketAddress)e.getRemoteAddress()).getAddress(), interval);
                observedEntity.addObserver(entityObserver);

                //add the modified observed entity to the list of observations
                observedEntities.put(targetURI.getPath(), observedEntity);

            }
            catch(NumberFormatException ex){
                System.out.println("Number Format Exception for: " + value);
                sendHttpResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
                return;
            }
        } catch (URISyntaxException e1) {
            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    protected class ObservedEntity extends Observable {
        public String path;

        public ObservedEntity(String path){
            this.path = path;
        }
    }

    protected class EntityObserver implements Observer{

        InetAddress observerAddress;
        int interval;
        long lastTimeUpdateSent;

        public EntityObserver(InetAddress observerAdress, int interval){
            this.observerAddress = observerAdress;
            this.interval = interval * 120000;
            this.lastTimeUpdateSent = System.currentTimeMillis();
        }

        @Override
        public void update(Observable o, Object arg) {
            String observedEntityPath = (String) arg;

            pendingObservations.put(observedEntityPath, this);

//            HttpRequest request =
//                    new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, pathToObserverService);
//            HttpHeaders.setKeepAlive(request, false);
//            request.setContent(ChannelBuffers.wrappedBuffer(observedEntityPath.getBytes(Charset.forName("UTF-8"))));
//            clientBootstrap.getPipeline()
//                           .getChannel()
//                           .write(o, observerAddress);
        }
    }
}

