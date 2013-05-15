package eu.spitfire_project.smart_service_proxy.core.httpServer.webServices;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.05.13
 * Time: 14:13
 * To change this template use File | Settings | File Templates.
 */
public abstract class WebService {

    private String path;

    public WebService(String path){
        this.path = path;
    }

    public String getPath(){
        return path;
    }

    public abstract HttpResponse processHttpRequest(HttpRequest httpRequest);

}
