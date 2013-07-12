package eu.spitfire.ssp.gateway.simple;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.vocabulary.VCARD;
import eu.spitfire.ssp.http.webservice.HttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 13:29
 * To change this template use File | Settings | File Templates.
 */
public class SimpleHttpRequestProcessor implements HttpRequestProcessor{

    public static String DEFAULT_MODEL_LANGUAGE = "RDF/XML";
    public static String DEFAULT_RESPONSE_MIME_TYPE = "application/rdf+xml";

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private Model model;

    public SimpleHttpRequestProcessor(){
        String servicePath = "http://example.org/JohnSmith";
        model = ModelFactory.createDefaultModel();
        model.createResource(servicePath).addProperty(VCARD.FN, "John Smith");
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {

        log.debug("Received request for path {}.", httpRequest.getUri());

        HttpResponse httpResponse;

        if(httpRequest.getMethod() == HttpMethod.GET){
            String acceptHeader = httpRequest.getHeader("Accept");
            log.debug("Accept header of request: {}.", acceptHeader);

            String lang = DEFAULT_MODEL_LANGUAGE;
            String mimeType = DEFAULT_RESPONSE_MIME_TYPE;

            if(acceptHeader != null) {
                if(acceptHeader.indexOf("application/rdf+xml") != -1){
                    lang = "RDF/XML";
                    mimeType = "application/rdf+xml";
                }
                else if(acceptHeader.indexOf("application/xml") != -1){
                    lang = "RDF/XML";
                    mimeType = "application/xml";
                }
                else if(acceptHeader.indexOf("text/n3") != -1){
                    lang = "N3";
                    mimeType = "text/n3";
                }
                else if(acceptHeader.indexOf("text/turtle") != -1) {
                    lang = "TURTLE";
                    mimeType = "text/turtle";
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            model.write(byteArrayOutputStream, lang);

            //Create Payload and
            httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
            httpResponse.setHeader(CONTENT_TYPE, mimeType + "; charset=utf-8");
            httpResponse.setContent(ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray()));
            httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());
        }
        else{
            httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.METHOD_NOT_ALLOWED);

            httpResponse.setHeader(CONTENT_TYPE, DEFAULT_RESPONSE_MIME_TYPE + "; charset=utf-8");
            String message = "Method not allowed: " + httpRequest.getMethod();
            httpResponse.setContent(ChannelBuffers.wrappedBuffer(message.getBytes(Charset.forName("UTF-8"))));
            httpResponse.setHeader(CONTENT_LENGTH, httpResponse.getContent().readableBytes());
        }

        //Send response
        responseFuture.set(httpResponse);
    }
}
