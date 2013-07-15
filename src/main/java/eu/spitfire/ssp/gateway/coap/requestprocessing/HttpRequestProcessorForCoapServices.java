package eu.spitfire.ssp.gateway.coap.requestprocessing;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.InvalidMessageException;
import de.uniluebeck.itm.ncoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import de.uniluebeck.itm.ncoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.ncoap.message.options.ToManyOptionsException;
import eu.spitfire.ssp.Main;
import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.APP_SHDT;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.07.13
 * Time: 20:36
 * To change this template use File | Settings | File Templates.
 */
public class HttpRequestProcessorForCoapServices implements HttpRequestProcessor{

    private static Logger log = LoggerFactory.getLogger(HttpRequestProcessorForCoapServices.class.getName());

    private CoapClientApplication coapClientApplication;

    public HttpRequestProcessorForCoapServices(CoapClientApplication coapClientApplication){
        this.coapClientApplication = coapClientApplication;
    }

    public void processHttpRequest(final SettableFuture<HttpResponse> responseFuture, final HttpRequest httpRequest) {

        try{
            String coapAddress;
            String coapPath;

            if(Main.DNS_WILDCARD_POSTFIX != null){
                coapAddress = httpRequest.getHeader("HOST");

                //cut out the target host related part of the http request host
                coapAddress = getCoapTargetHost(coapAddress.substring(0, coapAddress.indexOf(".")));

                //set CoAP target path
                coapPath = httpRequest.getUri();
            }
            else{
                String[] pathParts = httpRequest.getUri().split("/");
                coapAddress = getCoapTargetHost(pathParts[1]);

                coapPath ="";
                for(int i = 2; i < pathParts.length; i++)
                    coapPath += "/" + pathParts[i];
            }

            URI coapUri = new URI("coap", coapAddress + ":5683", coapPath, null);
            log.debug("CoAP target URI: {}", coapUri);

            CoapRequest coapRequest = convertToCoapRequest(httpRequest, coapUri);

            coapClientApplication.writeCoapRequest(coapRequest, new CoapResponseProcessor() {
                @Override
                public void processCoapResponse(CoapResponse coapResponse) {
                    try {
                        responseFuture.set(convertToHttpResponse(coapResponse, httpRequest.getProtocolVersion()));
                    }
                    catch (Exception e) {
                        log.error("Exception while converting from CoAP to HTTP.", e);
                        responseFuture.set(createErrorResponse(httpRequest.getProtocolVersion(),
                                HttpResponseStatus.INTERNAL_SERVER_ERROR));
                    }
                }
            });

        }
        catch (Exception e) {
            log.error("Exception while converting from HTTP to CoAP!", e);
            responseFuture.set(createErrorResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR));
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
   

    private static HttpResponse createErrorResponse(HttpVersion version, HttpResponseStatus status){
        HttpResponse httpResponse = new DefaultHttpResponse(version, status);

        ChannelBuffer content = ChannelBuffers.wrappedBuffer(status.getReasonPhrase().
                                                                    getBytes(Charset.forName("UTF-8")));
        httpResponse.setContent(content);
        httpResponse.setHeader("Content-Length", content.readableBytes());

        return httpResponse;
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

    public static HttpResponse convertToHttpResponse(CoapResponse coapResponse, HttpVersion httpVersion) throws Exception {
        //convert status code / response code
        int responseCode = coapResponse.getHeader().getCode().number;
        HttpResponseStatus httpStatus = INTERNAL_SERVER_ERROR;
        switch (responseCode) {
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
        HttpResponse response = new DefaultHttpResponse(httpVersion, httpStatus);

//        //convert options / header
//        List<Option> options = coapResponse.getOptions().getOptionList();
//        for (Option o : options) {
//            switch (o.getNumber()) {
//                case 1:  String contentType = "";
//                    switch((int)((UintOption)o).getValue()) {
//                        case 00:  contentType = "text/plain; charset=utf-8"; break;
//                        case 01:  contentType = "text/xml; charset=utf-8"; break;
//                        case 02:  contentType = "text/csv; charset=utf-8"; break;
//                        case 03:  contentType = "text/html; charset=utf-8"; break;
//                        case 40:  contentType = "application/link-format"; break;
//                        case 41:  contentType = "application/xml"; break;
//                        case 42:  contentType = "application/octet-stream"; break;
//                        case 43:  contentType = "application/rdf+xml"; break;
//                        case 44:  contentType = "application/soap+xml"; break;
//                        case 45:  contentType = "application/atom+xml"; break;
//                        case 46:  contentType = "application/xmpp+xml"; break;
//                        case 47:  contentType = "application/exi"; break;
//                        case 48:  contentType = "application/fastinfoset"; break;
//                        case 49:  contentType = "application/soap+fastinfoset"; break;
//                        case 50:  contentType = "application/json"; break;
//                        case 51:  contentType = "application/x-obix-binary";
//                    }
//                    if (!contentType.isEmpty()) {
//                        response.setHeader(CONTENT_TYPE, contentType);
//                    }
//                    break;
//            }
//        }

        //convert content / payload
        response.setContent(coapResponse.getPayload());
        response.setHeader("Content-Length", response.getContent().readableBytes());

        log.debug("Http response {} created.", response.getStatus());
        return response;
    }
}
