package eu.spitfire.ssp.gateway;

import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 11:36
 * To change this template use File | Settings | File Templates.
 */
public class InternalRegisterServiceMessage {

    private URI proxyUri;
    private HttpRequestProcessor httpRequestProcessor;

    public InternalRegisterServiceMessage(URI proxyUri, HttpRequestProcessor httpRequestProcessor) {
        this.proxyUri = proxyUri;
        this.httpRequestProcessor = httpRequestProcessor;
    }

    public URI getProxyUri() {
        return proxyUri;
    }

    public HttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }
}
