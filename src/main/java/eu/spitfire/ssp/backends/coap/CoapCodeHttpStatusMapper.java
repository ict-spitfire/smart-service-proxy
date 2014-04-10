//package eu.spitfire.ssp.backends.coap;
//
//import de.uniluebeck.itm.ncoap.message.MessageCode;
//import org.jboss.netty.handler.codec.http.HttpResponseStatus;
//
//import java.util.HashMap;
//
//
///**
// * Abstract class to provide a mapping from {@link de.uniluebeck.itm.ncoap.message.MessageCode.Name} to the proper
// * {@link HttpResponseStatus}.
// *
// * @author Oliver Kleine
// */
//public abstract class CoapCodeHttpStatusMapper {
//
//    private static HashMap<MessageCode.Name, HttpResponseStatus> mapping = new HashMap<>();
//    static{
//        mapping.put(MessageCode.Name.CREATED_201,                       HttpResponseStatus.CREATED);
//        mapping.put(MessageCode.Name.DELETED_202,                       HttpResponseStatus.NO_CONTENT);
//        mapping.put(MessageCode.Name.VALID_203,                         HttpResponseStatus.NOT_MODIFIED);
//        mapping.put(MessageCode.Name.CHANGED_204,                       HttpResponseStatus.NO_CONTENT);
//        mapping.put(MessageCode.Name.CONTENT_205,                       HttpResponseStatus.OK);
//        mapping.put(MessageCode.Name.BAD_REQUEST_400,                   HttpResponseStatus.BAD_REQUEST);
//        mapping.put(MessageCode.Name.UNAUTHORIZED_401,                  HttpResponseStatus.UNAUTHORIZED);
//        mapping.put(MessageCode.Name.BAD_OPTION_402,                    HttpResponseStatus.BAD_REQUEST);
//        mapping.put(MessageCode.Name.FORBIDDEN_403,                     HttpResponseStatus.FORBIDDEN);
//        mapping.put(MessageCode.Name.NOT_FOUND_404,                     HttpResponseStatus.NOT_FOUND);
//        mapping.put(MessageCode.Name.METHOD_NOT_ALLOWED_405,            HttpResponseStatus.METHOD_NOT_ALLOWED);
//        mapping.put(MessageCode.Name.NOT_ACCEPTABLE_406,                HttpResponseStatus.NOT_ACCEPTABLE);
//        mapping.put(MessageCode.Name.PRECONDITION_FAILED_412,           HttpResponseStatus.PRECONDITION_FAILED);
//        mapping.put(MessageCode.Name.REQUEST_ENTITY_TOO_LARGE_413,      HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
//        mapping.put(MessageCode.Name.UNSUPPORTED_CONTENT_FORMAT_415,    HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
//        mapping.put(MessageCode.Name.INTERNAL_SERVER_ERROR_500,         HttpResponseStatus.INTERNAL_SERVER_ERROR);
//        mapping.put(MessageCode.Name.NOT_IMPLEMENTED_501,               HttpResponseStatus.NOT_IMPLEMENTED);
//        mapping.put(MessageCode.Name.BAD_GATEWAY_502,                   HttpResponseStatus.BAD_GATEWAY);
//        mapping.put(MessageCode.Name.SERVICE_UNAVAILABLE_503,           HttpResponseStatus.SERVICE_UNAVAILABLE);
//        mapping.put(MessageCode.Name.GATEWAY_TIMEOUT_504,               HttpResponseStatus.GATEWAY_TIMEOUT);
//        mapping.put(MessageCode.Name.PROXYING_NOT_SUPPORTED_505,        HttpResponseStatus.BAD_GATEWAY);
//    }
//
//    /**
//     * Returns the HTTP response status semantically equivalent (or similar) to the given CoAP code
//     *
//     * @param messageCode a CoAP response code
//     *
//     * @return a HTTP response status or null if no proper status was found
//     */
//    public static HttpResponseStatus getHttpResponseStatus(MessageCode.Name messageCode){
//        return mapping.get(messageCode);
//    }
//}
