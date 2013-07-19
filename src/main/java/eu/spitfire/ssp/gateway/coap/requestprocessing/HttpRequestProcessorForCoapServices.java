package eu.spitfire.ssp.gateway.coap.requestprocessing;

import com.google.common.collect.Multimap;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.core.payloadserialization.Language;
import eu.spitfire.ssp.core.payloadserialization.ModelSerializer;
import eu.spitfire.ssp.core.payloadserialization.ShdtDeserializer;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.APP_SHDT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * @author Oliver Kleine
 */
public class HttpRequestProcessorForCoapServices implements HttpRequestProcessor{

    public static final int COAP_SERVER_PORT = 5683;
    private static Logger log = LoggerFactory.getLogger(HttpRequestProcessorForCoapServices.class.getName());

    private CoapClientApplication coapClientApplication;

    public HttpRequestProcessorForCoapServices(){
        this.coapClientApplication = new CoapClientApplication();
    }

    @Override
    public void processHttpRequest(final SettableFuture<HttpResponse> responseFuture, final HttpRequest httpRequest) {

        try{
            String coapAddress;
            String coapPath;

            log.debug("Host: {}", httpRequest.getHeader(HttpHeaders.Names.HOST));

            if(httpRequest.getHeader(HttpHeaders.Names.HOST).endsWith(":" + COAP_SERVER_PORT)){
                coapAddress = httpRequest.getHeader(HttpHeaders.Names.HOST).replaceFirst(":" + COAP_SERVER_PORT, "");
                coapPath = httpRequest.getUri();
            }
            else if(Main.DNS_WILDCARD_POSTFIX != null){
                coapAddress = httpRequest.getHeader(HttpHeaders.Names.HOST);

                //cut out the target host related part of the http request host
                coapAddress = getCoapTargetHost(coapAddress.substring(0, coapAddress.indexOf(".")));

                //set CoAP target path
                coapPath = httpRequest.getUri();
            }
            else{
                String[] pathParts = httpRequest.getUri().split("/");
                coapAddress = getCoapTargetHost(pathParts[2]);

                coapPath ="";
                for(int i = 3; i < pathParts.length; i++)
                    coapPath += "/" + pathParts[i];
            }

            URI coapUri = new URI("coap", coapAddress + ":" + COAP_SERVER_PORT, coapPath, null);
            log.debug("CoAP target URI: {}", coapUri);

            CoapRequest coapRequest = convertToCoapRequest(httpRequest, coapUri);

            log.debug("Send CoAP request: {}", coapRequest);

            coapClientApplication.writeCoapRequest(coapRequest, new CoapResponseProcessor() {
                @Override
                public void processCoapResponse(CoapResponse coapResponse) {

                    log.debug("Process CoAP response: {}", coapResponse);

                    HttpResponse httpResponse;

                    try {
                        String accept = httpRequest.getHeader("Accept");
                        log.debug("Accept header of HTTP request: {}", accept);
                        Language accepted = Language.getByHttpMimeType(accept);
                        if(accepted == null)
                            httpResponse = HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                                    HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, httpRequest.getHeader("Accept"));
                        else
                            httpResponse =
                                    convertToHttpResponse(coapResponse, httpRequest.getProtocolVersion(), accepted);
                    }
                    catch (Exception e) {
                        log.error("Exception while converting from CoAP to HTTP.", e);
                        httpResponse =
                                HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);

                    }

                    responseFuture.set(httpResponse);
                }
            });
        }
        catch (Exception e) {
            log.error("Exception while converting from HTTP to CoAP!", e);
            HttpResponse httpResponse =
                    HttpResponseFactory.createHttpErrorResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
            responseFuture.set(httpResponse);
        }
    }
    
    private String getCoapTargetHost(String ipString) throws URISyntaxException{

        //check if it's an IPv4 address
        ipString = ipString.replace("-", ".");
        if(InetAddresses.isInetAddress(ipString))
            return ipString;

        //check if it's an IPv6 address
        ipString = ipString.replace(".", ":");
        if(InetAddresses.isInetAddress(ipString.replace("-", ":")))
            return "[" + ipString.replace("-", ":") + "]";

        throw new URISyntaxException(ipString.replaceAll(":", "-"), "Could not get IP!");
    }
   
    public static CoapRequest convertToCoapRequest(HttpRequest httpRequest, URI targetURI) throws Exception{

        //convert method
        Code code;
        HttpMethod method = httpRequest.getMethod();
        if (method.equals(HttpMethod.GET)) {
            code = Code.GET;
        } else if(method.equals(HttpMethod.DELETE)) {
            code = Code.DELETE;
        } else if(method.equals(HttpMethod.PUT)) {
            code = Code.PUT;
        } else if(method.equals(HttpMethod.POST)) {
            code = Code.POST;
        } else {
            throw new MethodNotAllowedException(httpRequest.getMethod());
        }

        CoapRequest coapRequest = new CoapRequest(MsgType.CON, code, targetURI);

        if(code == Code.POST || code == Code.PUT){
            if(httpRequest.getContent().readableBytes() > 0)
                coapRequest.setPayload(httpRequest.getContent());
        }

        //TODO Set CoAP "Accept-Options" according to the HTTP "Accept-Header"

        coapRequest.setAccept(APP_SHDT);

        return coapRequest;
    }

    public static HttpResponse convertToHttpResponse(CoapResponse coapResponse, HttpVersion httpVersion,
                                                     Language payloadMimeType){

        //convert status code / response code
        HttpResponseStatus httpStatus = INTERNAL_SERVER_ERROR;
        switch (coapResponse.getHeader().getCode().number) {
            case 65:  httpStatus = CREATED; break;
            case 66:  httpStatus = NO_CONTENT; break;
            case 67:  httpStatus = NOT_MODIFIED; break;
            case 68:  httpStatus = NO_CONTENT; break;
            case 69:  httpStatus = OK; break;
            case 128: httpStatus = BAD_REQUEST; break;
            case 129: httpStatus = BAD_REQUEST; break;
            case 130: httpStatus = BAD_REQUEST; break;
            case 131: httpStatus = FORBIDDEN; break;
            case 132: httpStatus = NOT_FOUND; break;
            case 133: httpStatus = METHOD_NOT_ALLOWED; break;
            case 141: httpStatus = REQUEST_ENTITY_TOO_LARGE; break;
            case 143: httpStatus = UNSUPPORTED_MEDIA_TYPE; break;
            case 160: httpStatus = INTERNAL_SERVER_ERROR; break;
            case 161: httpStatus = NOT_IMPLEMENTED; break;
            case 162: httpStatus = BAD_GATEWAY; break;
            case 163: httpStatus = SERVICE_UNAVAILABLE; break;
            case 164: httpStatus = GATEWAY_TIMEOUT; break;
            case 165: httpStatus = BAD_GATEWAY; break;
        }


        //check for obvious payload errors
        if(coapResponse.getContentType() == null)
            return HttpResponseFactory.createHttpErrorResponse(httpVersion, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "CoAP response without content type option.");

        if(coapResponse.getCode().isErrorMessage())
            return HttpResponseFactory.createHttpErrorResponse(httpVersion, httpStatus,
                    "CoAP response had error code " + coapResponse.getCode());


        //read payload from CoAP response
        byte[] coapPayload = new byte[coapResponse.getPayload().readableBytes()];
        coapResponse.getPayload().getBytes(0, coapPayload);

        //Create Jena Model from CoAP response
        Model model = ModelFactory.createDefaultModel();
        if(coapResponse.getContentType() == MediaType.APP_SHDT){

            log.debug("SHDT payload in CoAP response.");

            try{
                (new ShdtDeserializer(64)).read_buffer(model, coapPayload);
            }
            catch(Exception e){
                log.error("SHDT error!", e);
                return HttpResponseFactory.createHttpErrorResponse(httpVersion,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
            }
        }
        else{
            try{
                Language language = Language.getByCoapMediaType(coapResponse.getContentType());
                model.read(new ChannelBufferInputStream(coapResponse.getPayload()), null, language.lang);
            }
            catch(Exception e){
                log.error("Error while reading CoAP response payload into model. ", e);
                return HttpResponseFactory.createHttpErrorResponse(httpVersion,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
            }
        }

        //Serialize payload for HTTP response
        ChannelBuffer httpPayload = ModelSerializer.serializeModel(model, payloadMimeType);

        Multimap<String, String> httpHeaders = CoapOptionHttpHeaderMapper.getHttpHeaders(coapResponse.getOptionList());

        return HttpResponseFactory.createHttpResponse(httpVersion, httpStatus, httpHeaders, httpPayload);

    }
}
