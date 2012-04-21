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

import com.hp.hpl.jena.n3.turtle.TurtleParseException;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import eu.spitfire_project.smart_service_proxy.utils.HttpResponseFactory;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

/**
 * The FilesBackend is responsible for the services backed by the files (except of *.swp) in the directory given as
 * constructor parameter. That means every file gets a URI to be requested via GET request to read its content.
 * There are only GET requests supported. Other methods but GET cause a response
 * with status "method not allowed". GET requests on resources backed by malformed files (i.e. content
 * is anything but valid "N3") cause an "internal server error" response.
 *
 * @author Oliver Kleine
 * @author Henning Hasemann
 */
public class FilesBackend extends Backend {
    
    private static Logger log = Logger.getLogger(FilesBackend.class.getName());
    
    private String directory;
    
    private HashMap<URI, File> resources = new HashMap<URI, File>();

    /**
     * Constructor for a new FileBackend instance which provides all files in the specified directory
     * as resources.
     *
     * @param directory the path to the directory where the files are located
     */
    public FilesBackend(String directory){
        super();
        this.directory = directory;
    }
    
    @Override
	public void bind(EntityManager em) {
		super.bind(em);
        registerFileResources();
    }

    //Register all files as new resources at the EntityManager (ignore *.swp files)
    private void registerFileResources(){
        File directoryFile = new File(directory);
        File[] files = directoryFile.listFiles();
        
        if(files != null){
            
            if(files.length == 0){
                log.info("[FilesBackend] Directory is empty: " + directory);
            }
            
            for(File file : files){
                if(!file.getName().endsWith(".swp")){
                    try{
                        URI resourceURI = new URI(entityManager.getURIBase() + pathPrefix + file.getName());
                          
                        resources.put(resourceURI, file);

                        if(log.isDebugEnabled()){
                            log.debug("[FilesBackend] Added file " + file.getAbsolutePath() +
                                    " as new resource at " + resourceURI);
                        }

                    } catch (URISyntaxException e) {
                        log.fatal("[FilesBackend] This should never happen.", e);
                    }
                }
            }
        }
        else{
            log.fatal("[FilesBackend] Directory does not exist: " + directory);    
        }
    }

    @Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws Exception {
        if(!(me.getMessage() instanceof HttpRequest)){
            ctx.sendUpstream(me);
        }

		HttpRequest request = (HttpRequest) me.getMessage();
        Object response;
                   
        //Look up file
        URI resourceURI = entityManager.normalizeURI(new URI(request.getUri()));
        File file = resources.get(resourceURI);

        if(file != null && file.isFile()){

            if(request.getMethod() == HttpMethod.GET){

                //Read file content and write it to the model
                Model model = ModelFactory.createDefaultModel();
                FileInputStream inputStream = new FileInputStream(file);

                try{
                    model.read(inputStream, resourceURI.toString(), "N3");
                    response = new SelfDescription(model, resourceURI, new Date());
                }
                catch(TurtleParseException e){
                    response = HttpResponseFactory.createHttpResponse(request.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);

                    if(log.isDebugEnabled()){
                        log.debug("[FilesBackend] Malformed file content in: " + file.getAbsolutePath(), e);
                    }
                }
            }
            else {
                response = HttpResponseFactory.createHttpResponse(request.getProtocolVersion(),
                        HttpResponseStatus.METHOD_NOT_ALLOWED);

                if(log.isDebugEnabled()){
                    log.debug("[FilesBackend] File not found: " + file.getAbsolutePath());
                }
            }
        }
        else{
            response = HttpResponseFactory.createHttpResponse(request.getProtocolVersion(),
                    HttpResponseStatus.NOT_FOUND);
        }
        
        ChannelFuture future = Channels.write(ctx.getChannel(), response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public Set<URI> getResources(){
        return resources.keySet();
    }
}

