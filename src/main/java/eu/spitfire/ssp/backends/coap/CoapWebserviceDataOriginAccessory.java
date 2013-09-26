package eu.spitfire.ssp.backends.coap;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.header.Code;
import de.uniluebeck.itm.ncoap.message.header.MsgType;
import eu.spitfire.ssp.backends.DataOriginAccessory;
import eu.spitfire.ssp.backends.DataOriginResponseMessage;
import eu.spitfire.ssp.server.webservices.MethodNotAllowedException;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

import static de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType.*;


/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 23.09.13
 * Time: 23:04
 * To change this template use File | Settings | File Templates.
 */
public class CoapWebserviceDataOriginAccessory implements DataOriginAccessory<URI> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private CoapBackendComponentFactory backendManager;

    public CoapWebserviceDataOriginAccessory(CoapBackendComponentFactory backendManager){
        this.backendManager = backendManager;
    }

    @Override
    public void processHttpRequest(SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture,
                                   HttpRequest httpRequest, URI dataOrigin) {
        try {
            CoapRequest coapRequest = convertHttpRequest(httpRequest, dataOrigin);
            CoapResponseProcessor coapResponseProcessor =
                    new CoapWebserviceResponseProcessor(dataOriginResponseFuture, dataOrigin);
            backendManager.getCoapClientApplication().writeCoapRequest(coapRequest, coapResponseProcessor);
        } catch (Exception e) {
            log.error("Error while processing HTTP request for CoAP data origin.", e);
            dataOriginResponseFuture.setException(e);
        }
    }

    /**
     * Converts the given {@link HttpRequest} to a proper {@link CoapRequest}.
     *
     * @param httpRequest the {@link HttpRequest} to be converted
     * @param serviceUri the URI of the CoAP Webservice
     *
     * @return the {@link CoapRequest} to be performed on the CoAP Webservice
     *
     * @throws Exception
     */
    public CoapRequest convertHttpRequest(HttpRequest httpRequest, URI serviceUri) throws Exception {
        //convert method
        Code code;
        HttpMethod method = httpRequest.getMethod();

        if (method.equals(HttpMethod.GET))
            code = Code.GET;
        else if(method.equals(HttpMethod.DELETE))
            code = Code.DELETE;
        else if(method.equals(HttpMethod.PUT))
            code = Code.PUT;
        else if(method.equals(HttpMethod.POST))
            code = Code.POST;
        else
            throw new MethodNotAllowedException(httpRequest.getMethod());

        CoapRequest coapRequest = new CoapRequest(MsgType.CON, code, serviceUri);

        if(code == Code.POST || code == Code.PUT){
            if(httpRequest.getContent().readableBytes() > 0)
                coapRequest.setPayload(httpRequest.getContent());
        }

        coapRequest.setAccept(APP_SHDT);
        coapRequest.setAccept(APP_RDF_XML);
        coapRequest.setAccept(APP_N3);
        coapRequest.setAccept(APP_TURTLE);

        return coapRequest;
    }
}
//    @Override
//    public void readModel(final SettableFuture<CoapResponse> modelFromDataOriginFuture, URI serviceUri) {
//        try {
//            //Create CoAP request
//            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, serviceUri);
//            coapRequest.setAccept(MediaType.APP_RDF_XML, MediaType.APP_N3, MediaType.APP_TURTLE, MediaType.APP_SHDT);
//
//            //Create response processor instance to process the incoming response
//            CoapWebserviceResponseProcessor coapResponseProcessor =
//                    new CoapWebserviceResponseProcessor(modelFromDataOriginFuture, serviceUri);
//
//            //Write the CoAP request
//            backendComponentFactory.getCoapClientApplication().writeCoapRequest(coapRequest, coapResponseProcessor);
//        }
//        catch (Exception e) {
//            log.error("Could not retrieve model from {}", serviceUri, e);
//            modelFromDataOriginFuture.setException(e);
//        }
//    }


