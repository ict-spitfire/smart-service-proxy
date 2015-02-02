package eu.spitfire.ssp.backends.external.coap;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.application.client.Token;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Date;

/**
 * A {@link CoapAccessor} is the component to access external
 * {@link eu.spitfire.ssp.backends.external.coap.CoapWebservice}s, i.e. send GET, POST, PUT, or DELETE
 * messages. Currently, only GET is supported.
 *
 * @author Oliver Kleine
 */
public class CoapAccessor extends Accessor<URI, CoapWebservice> {

    private CoapClientApplication coapClient;

    /**
     * Creates a new instance of {@link CoapAccessor}
     *
     * @param componentFactory the {@link eu.spitfire.ssp.backends.external.coap.CoapBackendComponentFactory} to
     *                         provide the appropriate resources
     */
    public CoapAccessor(CoapBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.coapClient = componentFactory.getCoapClient();
    }


    @Override
    public ListenableFuture<DataOriginInquiryResult> getStatus(CoapWebservice coapWebservice){
        final SettableFuture<DataOriginInquiryResult> resultFuture = SettableFuture.create();

        try{
            URI webserviceUri = coapWebservice.getIdentifier();
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, webserviceUri);
            coapRequest.setAccept(ContentFormat.APP_RDF_XML);
            coapRequest.setAccept(ContentFormat.APP_N3);
            coapRequest.setAccept(ContentFormat.APP_TURTLE);

            InetAddress remoteAddress = InetAddress.getByName(webserviceUri.getHost());
            int port = webserviceUri.getPort() == -1 ? 5683 : webserviceUri.getPort();

            coapClient.sendCoapRequest(coapRequest,
                new ResponseProcessor(resultFuture, webserviceUri), new InetSocketAddress(remoteAddress, port)
            );
        }
        catch(Exception ex){
            resultFuture.setException(ex);
        }

        return resultFuture;
    }


    private class ResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor {

        private SettableFuture<DataOriginInquiryResult> resultFuture;
        private URI webserviceUri;

        private ResponseProcessor(SettableFuture<DataOriginInquiryResult> resultFuture, URI webserviceUri) {
            this.resultFuture = resultFuture;
            this.webserviceUri = webserviceUri;
        }


        @Override
        public void processCoapResponse(CoapResponse coapResponse) {
            try{
                Model model = CoapTools.getModelFromCoapResponse(coapResponse);
                Date expiry = new Date(System.currentTimeMillis() + coapResponse.getMaxAge() * 1000);

                resultFuture.set(new ExpiringNamedGraph(webserviceUri, model, expiry));
            }
            catch(Exception ex){
                resultFuture.set(new DataOriginAccessError(
                        AccessResult.Code.INTERNAL_ERROR, ex.getMessage()
                ));
            }
        }


        @Override
        public void processRetransmissionTimeout(InetSocketAddress remoteEndpoint, int messageID, Token token) {
            resultFuture.set(new DataOriginAccessError(
                    AccessResult.Code.TIMEOUT, "No response received from " + webserviceUri + " (Request timed out.)"
            ));
        }
    }
}
