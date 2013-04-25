package eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.webservice.NotObservableWebService;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import eu.spitfire_project.smart_service_proxy.backends.coap.noderegistration.annotation.AutoAnnotation;
import eu.spitfire_project.smart_service_proxy.utils.TString;
import org.apache.log4j.Logger;
import sun.net.util.IPAddressUtil;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.concurrent.*;

import static eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 12.04.13
 * Time: 15:30
 * To change this template use File | Settings | File Templates.
 */
class CoapNodeRegistrationService extends NotObservableWebService<Boolean> {

    private static Logger log = Logger.getLogger(CoapNodeRegistrationService.class.getName());

    private int AnnotationCount = 0; //This is for annotation debug

    CoapNodeRegistrationService(){
        super("/here_i_am", Boolean.TRUE);
    }

    @Override
    public CoapResponse processMessage(CoapRequest request, InetSocketAddress remoteAddress) {

        //Only POST messages are allowed
        if(request.getCode() != Code.POST){
            return new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
        }
        else{
            //As the here_i_am message is received, Auto Annotation is called to register this sensor
            //to the fuzzy set database. This is only an one-time call.
            autoAnnotation((Inet6Address)remoteAddress.getAddress());
            log.debug("Annotation counter: "+ ++AnnotationCount);

            try {
                log.debug("Registration request from " + remoteAddress.getAddress());
                CoapResourceDiscoverer resourceDiscoverer = new CoapResourceDiscoverer(remoteAddress.getAddress());

                //wait 2 minutes to discover new resources
                resourceDiscoverer.getFuture().get(2, TimeUnit.MINUTES);
                return new CoapResponse(Code.CREATED_201);

            }
            catch (InterruptedException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
            }
            catch (ExecutionException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
            }
            catch (TimeoutException e) {
                log.error("Error while waiting for /.well-known/core resource.", e);
                return new CoapResponse(Code.GATEWAY_TIMEOUT_504);
            }
        }
    }

    public void autoAnnotation(Inet6Address remoteAddress){
        //----------- fuzzy annotation and visualizer----------------------
        String ipv6Addr = remoteAddress.getHostAddress();
        if(ipv6Addr.indexOf("%") != -1){
            ipv6Addr = ipv6Addr.substring(0, ipv6Addr.indexOf("%"));
        }
        TString mac = new TString(ipv6Addr,':');
        String macAddr = mac.getStrAtEnd();

        log.debug("MACAddr is " + macAddr);

        if(IPAddressUtil.isIPv6LiteralAddress(ipv6Addr)){
            ipv6Addr = "[" + ipv6Addr + "]";
        }

        //URI of the minimal service (containg light value) of the new sensor
        String httpRequestUri = null;
        try {
            URI uri = createHttpURIs((Inet6Address) remoteAddress, "/light/_minimal")[0];
            httpRequestUri = uri.toString();
        }
        catch (URISyntaxException e) {
            log.error("Exception", e);
        }

        //httpTargetURI = "http://" + httpTargetURI+":8080/light/_minimal";
        log.debug("HTTP URI for minimal service: " + httpRequestUri);

        //String FOI = "";

//        while (coapRequest.getPayload().readable())
//            FOI += (char)coapRequest.getPayload().readByte();
//        log.debug("FOI full: "+FOI);
//        TString tfoi = new TString(FOI,'/');
//        String foi = tfoi.getStrAtEnd();
//        FOI = foi.substring(0, foi.length()-1);
//        log.debug("FOI extracted: " + FOI);

        AutoAnnotation.getInstance().addNewEntryToDB(ipv6Addr, macAddr, httpRequestUri);
    }
}
