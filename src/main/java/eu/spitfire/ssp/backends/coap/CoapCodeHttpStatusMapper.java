package eu.spitfire.ssp.backends.coap;

import de.uniluebeck.itm.ncoap.message.header.Code;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.HashMap;


/**
 * Abstract class to provide a mapping from {@link Code} to the proper {@link HttpResponseStatus}.
 *
 * @author Oliver Kleine
 */
public abstract class CoapCodeHttpStatusMapper {

    private static HashMap<Code, HttpResponseStatus> mapping = new HashMap();
    static{
        mapping.put(Code.CREATED_201, HttpResponseStatus.CREATED);
        mapping.put(Code.DELETED_202, HttpResponseStatus.NO_CONTENT);
        mapping.put(Code.VALID_203, HttpResponseStatus.NOT_MODIFIED);
        mapping.put(Code.CHANGED_204, HttpResponseStatus.NO_CONTENT);
        mapping.put(Code.CONTENT_205, HttpResponseStatus.OK);
        mapping.put(Code.BAD_REQUEST_400, HttpResponseStatus.BAD_REQUEST);
        mapping.put(Code.UNAUTHORIZED_401, HttpResponseStatus.UNAUTHORIZED);
        mapping.put(Code.BAD_OPTION_402, HttpResponseStatus.BAD_REQUEST);
        mapping.put(Code.FORBIDDEN_403, HttpResponseStatus.FORBIDDEN);
        mapping.put(Code.NOT_FOUND_404, HttpResponseStatus.NOT_FOUND);
        mapping.put(Code.METHOD_NOT_ALLOWED_405, HttpResponseStatus.METHOD_NOT_ALLOWED);
        mapping.put(Code.NOT_ACCEPTABLE, HttpResponseStatus.NOT_ACCEPTABLE);
        mapping.put(Code.PRECONDITION_FAILED_412, HttpResponseStatus.PRECONDITION_FAILED);
        mapping.put(Code.REQUEST_ENTITY_TOO_LARGE_413, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
        mapping.put(Code.UNSUPPORTED_MEDIA_TYPE_415, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
        mapping.put(Code.INTERNAL_SERVER_ERROR_500, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        mapping.put(Code.NOT_IMPLEMENTED_501, HttpResponseStatus.NOT_IMPLEMENTED);
        mapping.put(Code.BAD_GATEWAY_502, HttpResponseStatus.BAD_GATEWAY);
        mapping.put(Code.SERVICE_UNAVAILABLE_503, HttpResponseStatus.SERVICE_UNAVAILABLE);
        mapping.put(Code.GATEWAY_TIMEOUT_504, HttpResponseStatus.GATEWAY_TIMEOUT);
        mapping.put(Code.PROXYING_NOT_SUPPORTED_505, HttpResponseStatus.BAD_GATEWAY);
    }

    /**
     * Returns the HTTP response status semantically equivalent (or similar) to the given CoAP code
     *
     * @param coapCode a CoAP response code
     *
     * @return a HTTP response status or null if no proper status was found
     */
    public static HttpResponseStatus getHttpResponseStatus(Code coapCode){
        return mapping.get(coapCode);
    }
}
