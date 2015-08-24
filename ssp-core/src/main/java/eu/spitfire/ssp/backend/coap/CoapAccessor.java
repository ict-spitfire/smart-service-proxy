package eu.spitfire.ssp.backend.coap;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.application.client.CoapResponseProcessor;
import de.uniluebeck.itm.ncoap.application.client.Token;
import de.uniluebeck.itm.ncoap.communication.reliability.outgoing.RetransmissionTimeoutProcessor;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import eu.spitfire.ssp.backend.generic.Accessor;

import eu.spitfire.ssp.server.internal.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.message.exception.OperationTimeoutException;
import org.apache.jena.rdf.model.Model;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Date;

/**
 * A {@link CoapAccessor} is the component to access external
 * {@link CoapWebservice}s, i.e. send GET, POST, PUT, or DELETE
 * message. Currently, only GET is supported.
 *
 * @author Oliver Kleine
 */
public class CoapAccessor extends Accessor<URI, CoapWebservice> {

    private CoapClientApplication coapClient;

    /**
     * Creates a new instance of {@link CoapAccessor}
     *
     * @param componentFactory the {@link CoapComponentFactory} to
     *                         provide the appropriate resources
     */
    public CoapAccessor(CoapComponentFactory componentFactory) {
        super(componentFactory);
        this.coapClient = componentFactory.getCoapClient();
    }


    @Override
    public ListenableFuture<ExpiringNamedGraph> getStatus(CoapWebservice coapWebservice){
        final SettableFuture<ExpiringNamedGraph> resultFuture = SettableFuture.create();

        try{
            URI webserviceUri = coapWebservice.getIdentifier();
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, webserviceUri);
            coapRequest.setAccept(ContentFormat.APP_RDF_XML);
            coapRequest.setAccept(ContentFormat.APP_N3);
            coapRequest.setAccept(ContentFormat.APP_TURTLE);

            InetAddress remoteAddress = InetAddress.getByName(webserviceUri.getHost());
            int port = webserviceUri.getPort() == -1 ? 5683 : webserviceUri.getPort();

            coapClient.sendCoapRequest(coapRequest,
                new InternalCoapResponseProcessor(resultFuture, webserviceUri), new InetSocketAddress(remoteAddress, port)
            );
        }
        catch(Exception ex){
            resultFuture.setException(ex);
        }

        return resultFuture;
    }


    private class InternalCoapResponseProcessor implements CoapResponseProcessor, RetransmissionTimeoutProcessor {

        private SettableFuture<ExpiringNamedGraph> resultFuture;
        private URI webserviceUri;

        private InternalCoapResponseProcessor(SettableFuture<ExpiringNamedGraph> resultFuture, URI webserviceUri) {
            this.resultFuture = resultFuture;
            this.webserviceUri = webserviceUri;
        }


        @Override
        public void processCoapResponse(CoapResponse coapResponse) {
            try{
                Model model = CoapTools.getModelFromCoapResponse(coapResponse);
                Date expiry = new Date(System.currentTimeMillis() + coapResponse.getMaxAge() * 1000);

                this.resultFuture.set(new ExpiringNamedGraph(this.webserviceUri, model, expiry));
            }
            catch(Exception ex){
                this.resultFuture.setException(ex);
            }
        }


        @Override
        public void processRetransmissionTimeout(InetSocketAddress remoteEndpoint, int messageID, Token token) {
            resultFuture.setException(new OperationTimeoutException(
                String.format("No response received from \"%s\" (Request timed out.)",  this.webserviceUri)
            ));
        }
    }
}
