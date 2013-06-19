package eu.spitfire.ssp.backends.coap.requestforwarding;

import de.uniluebeck.itm.spitfire.nCoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.communication.reliability.outgoing.RetransmissionTimeoutMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import eu.spitfire.ssp.backends.coap.CoapBackend;
import eu.spitfire.ssp.backends.coap.translation.Http2CoapConverter;
import eu.spitfire.ssp.core.SelfDescription;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.06.13
 * Time: 14:13
 * To change this template use File | Settings | File Templates.
 */
public class CoapClient extends CoapClientApplication{

    private Logger log = Logger.getLogger(this.getClass().getName());

    private CoapBackend coapBackend;

    public CoapClient(CoapBackend coapBackend){
        this.coapBackend = coapBackend;
    }

    @Override
    public void receiveResponse(CoapResponse coapResponse) {
        HttpResponse response;

        try{
            if(!(coapResponse.getCode().isErrorMessage()) && coapResponse.getPayload().readableBytes() > 0){
                response = new SelfDescription(coapResponse,
                        createHttpURIs(targetUriHostAddress, targetUriPath)[1]);
                log.debug("SelfDescription object created from response payload.");
            }
            else{
                log.debug("CoAP response is error message or without payload.");
                response = Http2CoapConverter.convertCoapToHttpResponse(coapResponse,
                        httpRequest.getProtocolVersion());
            }
        }
        catch(Exception e){
            log.error("Exception while receiving response.", e);
            response = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
            ((HttpResponse) response)
                    .setContent(ChannelBuffers.wrappedBuffer(coapResponse.getPayload()));
        }

        ChannelFuture future = Channels.write(ctx.getChannel(), response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void receiveEmptyACK() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleRetransmissionTimeout(RetransmissionTimeoutMessage timeoutMessage) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
