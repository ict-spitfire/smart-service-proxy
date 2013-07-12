package eu.spitfire.ssp.gateway.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.http.webservice.HttpRequestProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 12.07.13
 * Time: 15:40
 * To change this template use File | Settings | File Templates.
 */
public class FilesGatewayGui implements HttpRequestProcessor {



    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
