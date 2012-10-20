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

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.OptionRegistry;
import de.uniluebeck.itm.spitfire.nCoap.message.options.UintOption;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

import java.io.StringWriter;
import java.net.URI;
import java.util.Date;

/**
 * This is a helper class to be used by the ModelCache to relate URIs and Models and their expiry dates to each other
 * 
 * @author Oliver Kleine
 */

public class SelfDescription {
	
    private static Logger log = Logger.getLogger(SelfDescription.class.getName());
    
		private Model model;
        private String localURI;
		private Date expiry;
		private long observe;


		public SelfDescription(Model m, URI u, Date e){
			model = m;
			localURI = trimURI(u);
			expiry = e;
		}

		public SelfDescription(Model m, URI u){
			model = m;
			localURI = trimURI(u);
			// 5 minutes
			expiry = new Date((new Date()).getTime() + 5 * 60 * 1000);
		}

		public SelfDescription(CoapResponse coapResponse, URI uri) throws InvalidOptionException {
			//Set uri
			localURI = trimURI(uri);
            log.debug("[SelfDescription] Local URI: " + localURI);

			//Set model
			ChannelBuffer payload = coapResponse.getPayload();
			ChannelBufferInputStream istream = new ChannelBufferInputStream(payload);
			model = ModelFactory.createDefaultModel();
            
            UintOption contentTypeOption;
            try{
                contentTypeOption = (UintOption) coapResponse.getOption(OptionRegistry.OptionName.CONTENT_TYPE).get(0);
            }
            catch(IndexOutOfBoundsException e){
                log.fatal("[SelfDescription] CoapResponse did not contain a content type option. No SelfDescription" +
                        "object created!");
                throw e;
            }

            Long mediaTypeNumber = contentTypeOption.getDecodedValue();
            OptionRegistry.MediaType mediaType =
                    OptionRegistry.MediaType.getByNumber((long) mediaTypeNumber.intValue());

            if(mediaType == null){
                log.info("[SelfDescription] CoapResponse contains an unknown content type (number = " +
                        mediaTypeNumber + ").");
                throw new InvalidOptionException(OptionRegistry.OptionName.CONTENT_TYPE,
                                                 "Unknown content type: " + mediaTypeNumber);
            }

            //TODO add more media types
            //Try to create a Jena model from the message payload

			try {

			//ChannelBuffer buf = coapResponse.getResponsePayload();
			String lang = null;
            if(mediaType == OptionRegistry.MediaType.APP_N3){
			    lang = "N3";
				//ChannelBufferInputStream istream = new ChannelBufferInputStream(buf);
				model.read(istream, localURI, lang);
			}
			else if (mediaType == OptionRegistry.MediaType.APP_XML){
                lang = "RDF/XML";
				//ChannelBufferInputStream istream = new ChannelBufferInputStream(buf);
				model.read(istream, localURI, lang);
			}
			else if (mediaType == OptionRegistry.MediaType.APP_SHDT){
                log.debug("SHDT payload in CoAPResponse");
				byte[] bytebuffer = new byte[payload.readableBytes()];
				payload.getBytes(0, bytebuffer);
                try{
				    (new ShdtSerializer(64)).read_buffer(model, bytebuffer);
                }
                catch(Exception e){
                    log.error("SHDT error!", e);
                }
			}

            StringWriter writer = new StringWriter();
//------------TEST!
            try{
                model.write(writer, "RDF/XML");
                log.debug("[SelfDescription] Output after Model serialization (RDF/XML):\n " + writer.toString());
            }
            catch(Exception e){
                log.error("Could not write RDF/XML", e);
            }

            try{
                model.write(writer, "N3");
                log.debug("[SelfDescription] Output after Model serialization (N3):\n " + writer.toString());
            }
            catch(Exception e){
                log.error("Could not write N3", e);
            }
            //-------------

			//Set expiry
			long maxAge =
                    ((UintOption) coapResponse.getOption(OptionRegistry.OptionName.MAX_AGE).get(0)).getDecodedValue();
			//value is current time plus freshness duration
			expiry = new Date((new Date()).getTime() + maxAge * 1000);
            log.debug("[SelfDescription] Status of resource " + localURI + " expires on " + expiry +
                    " ( that means in " + maxAge + " seconds).");

			} catch(Exception e) {
				log.debug(e);
				e.printStackTrace();
			}

			//Set Observe value (only if resource is observable)
//			if(coapResponse.getOptions().getOption(10) != null){
//				observe = ((UintOption)coapResponse.getOptions().getOption(10)).getValue();
//				System.out.println("[SelfDescription] Observe value: " + observe);
//			}
//			else{
//				System.out.println("[SelfDescription] Observe value not set: " + observe);
//			}
		}

		public Date getExpiry(){
			return expiry;
		}

		public Model getModel(){
			return model;
		}
	
		public String getLocalURI(){
			return localURI;
		}
		
		public long getObserve(){
			return observe;
		}

		private String trimURI(URI u){
			String uri = u.toString();
			while(uri.endsWith("#")){
				uri = uri.substring(0, uri.length()-1);
			}
			return uri;
		}
}

