//package eu.spitfire.ssp.backends.external.coap.registry;
//
//import com.google.common.util.concurrent.SettableFuture;
//import com.hp.hpl.jena.rdf.model.Model;
//import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
//import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.InternalRetransmissionTimeoutMessage;
//import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
//import de.uniluebeck.itm.ncoap.message.CoapResponse;
//import de.uniluebeck.itm.ncoap.message.header.StatusCode;
//import eu.spitfire.ssp.backends.external.coap.CoapCodeHttpStatusMapper;
//import eu.spitfire.ssp.backends.external.coap.CoapResourceToolbox;
//import eu.spitfire.ssp.backends.generic.ExpiringModel;
//import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
//import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
//import org.jboss.netty.handler.codec.http.HttpResponseStatus;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.URI;
//import java.util.Date;
//import java.util.concurrent.ExecutorService;
//
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
//
///**
// * Created with IntelliJ IDEA.
// * User: olli
// * Date: 03.10.13
// * Time: 17:00
// * To change this template use File | Settings | File Templates.
// */
//public class RdfWebserviceResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor {
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private SettableFuture<ExpiringModel> expiringModelFuture;
//    private URI webserviceUri;
//    private ExecutorService internalTasksExecutorService;
//
//    public RdfWebserviceResponseProcessor(SettableFuture<ExpiringModel> expiringModelFuture, URI webserviceUri,
//                                          ExecutorService internalTasksExecutorService){
//        this.expiringModelFuture = expiringModelFuture;
//        this.webserviceUri = webserviceUri;
//        this.internalTasksExecutorService = internalTasksExecutorService;
//    }
//
//    @Override
//    public void processCoapResponse(final CoapResponse coapResponse) {
//        internalTasksExecutorService.submit(new Runnable(){
//            @Override
//            public void run() {
//                log.info("Process CoAP response: {}", coapResponse);
//                try{
//                    if(coapResponse.getCode().isErrorMessage()){
//                        StatusCode code = coapResponse.getCode();
//                        HttpResponseStatus httpResponseStatus = CoapCodeHttpStatusMapper.getHttpResponseStatus(code);
//                        String message = "CoAP response code from " + webserviceUri + " was " + code;
//                        throw new SemanticResourceException(webserviceUri, httpResponseStatus, message);
//                    }
//
//                    if(coapResponse.getPayload().readableBytes() > 0 && coapResponse.getContentType() == null){
//                        String message = "CoAP response had no content type option.";
//                        throw new SemanticResourceException(webserviceUri, INTERNAL_SERVER_ERROR, message);
//                    }
//
//                    if(coapResponse.getContentType() != null && coapResponse.getPayload().readableBytes() == 0){
//                        String message = "CoAP response had content type option but no content";
//                        throw new SemanticResourceException(webserviceUri, INTERNAL_SERVER_ERROR, message);
//                    }
//
//                    if(coapResponse.getPayload().readableBytes() > 0){
//                        Model model = CoapResourceToolbox.getModelFromCoapResponse(coapResponse);
//                        Date expiry = CoapResourceToolbox.getExpiryFromCoapResponse(coapResponse);
//
//                        ExpiringModel expiringModel = new ExpiringModel(model, expiry);
//                        expiringModelFuture.set(expiringModel);
//                    }
//                }
//                catch(Exception e){
//                    log.error("Error while processing response from {}.", webserviceUri, e);
//                    expiringModelFuture.setException(e);
//                }
//            }
//        });
//    }
//
//    @Override
//    public void processRetransmissionTimeout(InternalRetransmissionTimeoutMessage timeoutMessage) {
//        String message = "Request for service " + webserviceUri + " timed out.";
//        DataOriginAccessException exception =
//                new DataOriginAccessException(HttpResponseStatus.REQUEST_TIMEOUT, message);
//        expiringModelFuture.setException(exception);
//    }
//}
