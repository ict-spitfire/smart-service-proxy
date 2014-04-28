//package eu.spitfire.ssp.backends.coap;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.SettableFuture;
//
//import com.hp.hpl.jena.rdf.model.Model;
//import com.hp.hpl.jena.rdf.model.ModelFactory;
//
//import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
//import de.uniluebeck.itm.ncoap.application.client.Token;
//import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
//import de.uniluebeck.itm.ncoap.message.CoapResponse;
//import de.uniluebeck.itm.ncoap.message.MessageCode;
//import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
//
//import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
//import eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException;
//import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
//import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
//import eu.spitfire.ssp.utils.Language;
//
//import org.jboss.netty.channel.Channels;
//import org.jboss.netty.channel.local.LocalServerChannel;
//import org.jboss.netty.handler.codec.http.HttpResponseStatus;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.ByteArrayInputStream;
//
//import java.net.InetSocketAddress;
//import java.net.URI;
//
//import java.util.Date;
//import java.util.concurrent.ExecutorService;
//
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
//import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
//
//
//public class CoapWebserviceResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor{
//
//    private static Logger log = LoggerFactory.getLogger(CoapWebserviceResponseProcessor.class.getName());
//
//    private SettableFuture<InternalResourceStatusMessage> resourceStatusFuture;
//    private URI dataOrigin;
//    private URI resourceUri;
//    private ExecutorService backendTasksExecutorService;
//    private LocalServerChannel localServerChannel;
//
//
//    public CoapWebserviceResponseProcessor(CoapBackendComponentFactory backendComponentFactory, URI dataOrigin,
//                                           URI resourceUri){
//
//        this.localServerChannel = backendComponentFactory.getLocalChannel();
//        this.backendTasksExecutorService = backendComponentFactory.getInternalTasksExecutorService();
//
//        this.resourceStatusFuture = SettableFuture.create();
//        this.dataOrigin = dataOrigin;
//        this.resourceUri = resourceUri;
//    }
//
//
//    public ListenableFuture<InternalResourceStatusMessage> getResourceStatusFuture(){
//        return this.resourceStatusFuture;
//    }
//
//
//    /**
//     * Sets a value to the {@link SettableFuture} given as constructor parameter. This value is either a
//     * {@link eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage} which is generated from the incoming {@link CoapResponse} or an
//     * {@link eu.spitfire.ssp.backends.generic.exceptions.SemanticResourceException} if some error occurred.
//     *
//     * If there was no content in the response from the CoAP Webservice but the response code indicates success,
//     * the {@link SettableFuture} is set with null (for requests with method PUT, POST or DELETE ).
//     *
//     * @param coapResponse the response message
//     */
//    @Override
//    public void processCoapResponse(final CoapResponse coapResponse) {
//
//        backendTasksExecutorService.submit(new Runnable(){
//
//            @Override
//            public void run() {
//
//                log.info("Process CoAP response: {}", coapResponse);
//
//                try{
//
//                    if(MessageCode.isErrorMessage(coapResponse.getMessageCode())){
//
//                        MessageCode.Name messageCode = coapResponse.getMessageCodeName();
//                        HttpResponseStatus httpResponseStatus =
//                                CoapCodeHttpStatusMapper.getHttpResponseStatus(messageCode);
//
//                        String message = "CoAP response code from " + dataOrigin + " was " + messageCode;
//                        resourceStatusFuture.setException(
//                                new SemanticResourceException(dataOrigin, httpResponseStatus, message)
//                        );
//
//                        return;
//                    }
//
//                    if(coapResponse.getContent().readableBytes() > 0 &&
//                            coapResponse.getContentFormat() == ContentFormat.UNDEFINED){
//
//                        String message = "CoAP response had no content type option.";
//                        resourceStatusFuture.setException(
//                                new SemanticResourceException(dataOrigin, INTERNAL_SERVER_ERROR, message)
//                        );
//
//                        return;
//                    }
//
//                    if(coapResponse.getContent().readableBytes() == 0){
//                        String message = "CoAP response had no content!";
//                        resourceStatusFuture.setException(
//                            new SemanticResourceException(dataOrigin, INTERNAL_SERVER_ERROR, message)
//                        );
//
//                        return;
//                    }
//
//                    if(coapResponse.getContentFormat() == ContentFormat.UNDEFINED){
//                        String message = "CoAP response had no content format option!";
//                        resourceStatusFuture.setException(
//                                new SemanticResourceException(dataOrigin, INTERNAL_SERVER_ERROR, message)
//                        );
//
//                        return;
//                    }
//
//
//                    Model model = getModelFromCoapResponse(coapResponse);
//                    Date expiry = getExpiryFromCoapResponse(coapResponse);
//
//                    if(model == null){
//                        String message = "Resource " + resourceUri + " not found at data origin " + dataOrigin;
//                        resourceStatusFuture.setException(
//                                new DataOriginAccessException(INTERNAL_SERVER_ERROR, message)
//                        );
//
//                        return;
//                    }
//
//
//                    resourceStatusFuture.set(new InternalResourceStatusMessage(model, expiry));
//
//                }
//
//                catch(Exception e){
//                    log.error("Error while creating resource status message from CoAP response.", e);
//                    resourceStatusFuture.setException(e);
//                }
//            }
//        });
//    }
//
//
//
//    @Override
//    public void processRetransmissionTimeout(InetSocketAddress remoteEndpoint, int messageID, Token token) {
//        String exMessage = "No response received from " + dataOrigin + ".";
//        log.warn(exMessage);
//        resourceStatusFuture.setException(new DataOriginAccessException(GATEWAY_TIMEOUT, exMessage));
//
//        InternalRemoveResourcesMessage message = new InternalRemoveResourcesMessage(resourceUri);
//        Channels.write(localServerChannel, message);
//    }
//
//
//    /**
//     * Reads the payload of the given {@link de.uniluebeck.itm.ncoap.message.CoapResponse} into an instance of
//     * {@link com.hp.hpl.jena.rdf.model.Model} and returns that {@link com.hp.hpl.jena.rdf.model.Model}.
//     *
//     * @param coapResponse the {@link de.uniluebeck.itm.ncoap.message.CoapResponse} to read the payload from
//     *
//     * @return a {@link com.hp.hpl.jena.rdf.model.Model} containing the information from the payload or
//     * <code>null</code> if some error occurred.
//     *
//     */
//    protected static Model getModelFromCoapResponse(CoapResponse coapResponse){
//
//        try{
//            Model resourceStatus = ModelFactory.createDefaultModel();
//
//            //read payload from CoAP response
//            byte[] coapPayload = new byte[coapResponse.getContent().readableBytes()];
//            coapResponse.getContent().getBytes(0, coapPayload);
//
////            if(coapResponse.getContentFormat() == ContentFormat.APP_SHDT){
////                log.debug("SHDT payload in CoAP response.");
////                (new ShdtDeserializer(64)).read_buffer(resourceStatus, coapPayload);
////            }
//
//            Language language = Language.getByCoapContentFormat(coapResponse.getContentFormat());
//
//            if(language == null)
//                return null;
//
//            resourceStatus.read(new ByteArrayInputStream(coapPayload), null, language.lang);
//            return resourceStatus;
//        }
//
//        catch(Exception ex){
//            log.error("Could not read content from CoAP response!", ex);
//            return null;
//        }
//    }
//
//    /**
//     * Converts the max-age option from the given {@link CoapResponse} into a {@link java.util.Date}.
//     *
//     * @param coapResponse the {@link CoapResponse} to take its max-age option
//     *
//     * @return the {@link java.util.Date} the actual status of the resource expires accoording to the max-age option
//     * of the given {@link CoapResponse}
//     */
//    protected static Date getExpiryFromCoapResponse(CoapResponse coapResponse){
//
//        //Get expiry of resource
//        long maxAge = coapResponse.getMaxAge();
//        log.debug("Max-Age option of CoAP response: {}", maxAge);
//
//        return new Date(System.currentTimeMillis() + 1000 * maxAge);
//    }
//}
