package eu.spitfire_project.smart_service_proxy.backends.coap;

import de.uniluebeck.itm.spitfire.nCoap.application.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.OptionRegistry;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;

import java.net.Inet6Address;
import java.net.URI;
import java.net.URISyntaxException;


/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.04.13
 * Time: 09:43
 * To change this template use File | Settings | File Templates.
 */
public class CoapResourceObserver extends CoapClientApplication{

    private static Logger log =  Logger.getLogger(CoapResourceObserver.class.getName());

    private Channel channel;
    private Inet6Address serviceToObserveHost;
    private String serviceToObservePath;
    private URI httpMirrorURI;

    public CoapResourceObserver(Channel channel, Inet6Address observableServiceHost,
                                String observableServicePath){

        this.channel = channel;
        this.serviceToObserveHost = observableServiceHost;
        this.serviceToObservePath = observableServicePath;

    }

    public void writeRequestToObserveResource(){
        String targetURI = "coap://[" + serviceToObserveHost.getHostAddress() + "]" + serviceToObservePath;
        log.debug("Send request to observe " + targetURI);
        try {
            CoapRequest coapRequest = new CoapRequest(MsgType.CON, Code.GET, new URI(targetURI), this);
            coapRequest.setObserveOptionRequest();

            writeCoapRequest(coapRequest);

        } catch (Exception e) {
            log.error("Error while sending request to " + targetURI, e);
        }
    }

    @Override
    public void receiveResponse(CoapResponse coapResponse) {
        Object object;
        try {
            if(coapResponse.getOption(OptionRegistry.OptionName.OBSERVE_RESPONSE).isEmpty()
                    || coapResponse.getCode().isErrorMessage()){

                object = new ObservingFailedMessage(serviceToObserveHost, serviceToObservePath);
            }
            else{
                URI httpMirrorURI = (CoapBackend.createHttpURIs(serviceToObserveHost, serviceToObservePath))[1];
                object = new SelfDescription(coapResponse, httpMirrorURI);
            }
            channel.write(object);
         }
        catch (Exception e) {
           log.error("Error in update notification of observed service.", e);
           return;
        }

    }

    @Override
    public void receiveEmptyACK() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleRetransmissionTimout() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


}
