package eu.spitfire_project.smart_service_proxy.backends.coap.uberdust;

import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import eu.spitfire_project.smart_service_proxy.backends.coap.CoapBackend;
import eu.spitfire_project.smart_service_proxy.noderegistration.CoapNodeRegistrationServer;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import sun.net.util.IPAddressUtil;

import java.net.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 10.09.12
 * Time: 15:07
 * To change this template use File | Settings | File Templates.
 */
public class UberdustCoapServerBackend extends CoapBackend {

    private Logger log = Logger.getLogger(UberdustCoapServerBackend.class.getName());
    Configuration config;

    public UberdustCoapServerBackend(String pathPrefix, Configuration config) throws Exception {
        super(pathPrefix, config.getBoolean("coap.enableVirtualHttpServer", false));
        this.prefix = pathPrefix;
        this.config = config;
        log.debug("Prefix Uberdust: " + getIPv6Prefix());
        new Thread(new FakeRegistrationMessageSender(config)).start();
    }

    public URI createHttpMirrorURI(InetAddress remoteAddress, String path) throws URISyntaxException {
        String uberdustServerDns = config.getString("uberdustcoapserver.dnsName", remoteAddress.getHostAddress());
        String sspHostname = "";
        int sspPort;

        try {
            sspHostname = config.getString("SSP_DNS_NAME", InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
            log.error(e);
        }

        sspPort = config.getInt("SSP_HTTP_SERVER_PORT");
        return new URI("http://" + sspHostname + ":" + sspPort + "/" + uberdustServerDns + path + "#");
    }

    public URI createCoapTargetURI(String remoteIP, String path) throws URISyntaxException {
        if(IPAddressUtil.isIPv6LiteralAddress(remoteIP)){
            remoteIP = "[" + remoteIP + "]";
        }
        return new URI("coap://" + remoteIP + ":" + NODES_COAP_PORT + path);
    }

    /**
     * Returns the DNS name of the Uberdust server the backend is responsible for
     * @return the DNS name of the Uberdust server the backend is responsible for
     */
    public String getIPv6Prefix(){
        return prefix;
    }

    public class FakeRegistrationMessageSender implements Runnable{

        private Configuration config;

        public FakeRegistrationMessageSender(Configuration config){
            this.config = config;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(3000);

                String uberdustServerDnsName = config.getString("uberdustcoapserver.dnsName");
                if (uberdustServerDnsName == null){
                    throw new Exception("Property uberdustcoapserver.dnsName not set.");
                }

                int uberdustServerPort = config.getInt("uberdustcoapserver.port", 0);
                if (uberdustServerPort == 0){
                    throw new Exception("Property uberdustcoapserver.port not set.");
                }

                InetSocketAddress uberdustServerSocketAddress =
                        new InetSocketAddress(InetAddress.getByName(uberdustServerDnsName), uberdustServerPort);

                String baseURI = config.getString("SSP_DNS_NAME", "localhost");
                CoapRequest fakeRequest = new CoapRequest(MsgType.NON, Code.POST,
                        new URI("coap://" + baseURI + ":5683/here_i_am"));

                CoapNodeRegistrationServer registrationServer = CoapNodeRegistrationServer.getInstance();
                if (registrationServer == null){
                    log.error("NULL!");
                }
                registrationServer.receiveCoapRequest(fakeRequest, uberdustServerSocketAddress);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (InvalidMessageException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (InvalidOptionException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (ToManyOptionsException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }


        }
    }
}
