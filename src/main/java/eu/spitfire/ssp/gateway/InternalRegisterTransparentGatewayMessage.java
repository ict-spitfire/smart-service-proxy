package eu.spitfire.ssp.gateway;

import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 18.07.13
 * Time: 13:46
 * To change this template use File | Settings | File Templates.
 */
public class InternalRegisterTransparentGatewayMessage {

    private int port;
    private HttpRequestProcessor httpRequestProcessor;

    public InternalRegisterTransparentGatewayMessage(int port, HttpRequestProcessor httpRequestProcessor){
        this.port = port;
        this.httpRequestProcessor = httpRequestProcessor;
    }

    public int getPort() {
        return port;
    }

    public HttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }
}
