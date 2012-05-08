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
package eu.spitfire_project.smart_service_proxy.backends.coap;

import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.MessageDoesNotAllowPayloadException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.*;

import java.net.URI;
import java.net.URISyntaxException;

public class Http2CoapConverter {
    
    private static Logger log = Logger.getLogger(Http2CoapConverter.class.getName());
    
    public static CoapRequest convertHttpRequestToCoAPMessage(HttpRequest rq, URI targetURI) throws MethodNotAllowedException {
        //convert method
        Code code;
        HttpMethod method = rq.getMethod();
        if (method.equals(HttpMethod.GET)) {
            code = Code.GET;
        } else if(method.equals(HttpMethod.DELETE)) {
            code = Code.DELETE;
        } else if(method.equals(HttpMethod.PUT)) {
            code = Code.PUT;
        } else if(method.equals(HttpMethod.POST)) {
            code = Code.POST;
        } else {
            throw new MethodNotAllowedException(rq.getMethod());
        }

        CoapRequest coapRequest = null;
        try {
            coapRequest = new CoapRequest(MsgType.CON, code, targetURI);
        } catch (InvalidMessageException e) {
            log.fatal("[Http2CoapConverter] Error while creating CoapRequest. This should never happen.", e);
        } catch (ToManyOptionsException e) {
            log.fatal("[Http2CoapConverter] Error while creating CoapRequest. This should never happen.", e);
        } catch (InvalidOptionException e) {
            log.fatal("[Http2CoapConverter] Error while creating CoapRequest. This should never happen.", e);
        } catch (URISyntaxException e) {
            log.fatal("[Http2CoapConverter] Error while creating CoapRequest. This should never happen.", e);
        }

        if(code == Code.POST || code == Code.PUT){
            try {
                if(rq.getContent().readableBytes() > 0){
                    coapRequest.setPayload(rq.getContent());
                }
            } catch (MessageDoesNotAllowPayloadException e) {
                log.fatal("Error while converting payload of HttpRequest to CoapRequest!", e);
            }
        }

        //TODO Set CoAP "Accept-Options" according to the HTTP "Accept-Header"

        return coapRequest;
    }

    public static HttpResponse convertCoapToHttpResponse(CoapResponse coapResponse, HttpVersion httpVersion){
        HttpResponseStatus httpResponseStatus;

        //TODO map Response Codes
        return new DefaultHttpResponse(httpVersion, HttpResponseStatus.OK);
    }
    
//    private static HttpResponse convertCoAPMessageToHttpResponse(CoAPMessage coapResponse) throws Exception {
//        //convert status code / response code
//        int responseCode = coapResponse.getHeader().getCode().getNumber();
//        HttpResponseStatus httpStatus = OK;
//        switch (responseCode) {
//            case 65:  httpStatus = CREATED; break;
//            case 66:  httpStatus = NO_CONTENT; break;
//            case 67:  httpStatus = NOT_MODIFIED; break;
//            case 68:  httpStatus = NO_CONTENT; break;
//            case 69:  httpStatus = OK; break;
//            case 128: httpStatus = BAD_REQUEST; break;
//            case 129: httpStatus = BAD_REQUEST; break;
//            case 130: httpStatus = BAD_REQUEST; break;
//            case 131: httpStatus = FORBIDDEN; break;
//            case 132: httpStatus = NOT_FOUND; break;
//            case 133: httpStatus = METHOD_NOT_ALLOWED; break;
//            case 141: httpStatus = REQUEST_ENTITY_TOO_LARGE; break;
//            case 143: httpStatus = UNSUPPORTED_MEDIA_TYPE; break;
//            case 160: httpStatus = INTERNAL_SERVER_ERROR; break;
//            case 161: httpStatus = NOT_IMPLEMENTED; break;
//            case 162: httpStatus = BAD_GATEWAY; break;
//            case 163: httpStatus = SERVICE_UNAVAILABLE; break;
//            case 164: httpStatus = GATEWAY_TIMEOUT; break;
//            case 165: httpStatus = BAD_GATEWAY; break;
//        }
//        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, httpStatus);
//
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
//        //convert content / payload
//        response.setContent(coapResponse.getPayload());
//
//        return response;
//    }
}
