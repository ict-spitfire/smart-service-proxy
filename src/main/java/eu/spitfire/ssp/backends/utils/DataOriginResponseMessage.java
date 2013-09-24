package eu.spitfire.ssp.backends.utils;

import com.hp.hpl.jena.rdf.model.Model;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 24.09.13
 * Time: 12:47
 * To change this template use File | Settings | File Templates.
 */
public class DataOriginResponseMessage {

    private final HttpResponseStatus httpResponseStatus;
    private final Model model;
    private final Date expiry;

    public DataOriginResponseMessage(HttpResponseStatus httpResponseStatus){
        this(httpResponseStatus, null, null);
    }

    public DataOriginResponseMessage(HttpResponseStatus httpResponseStatus, Model model, Date expiry){
        this.httpResponseStatus = httpResponseStatus;
        this.model = model;
        this.expiry = expiry;    }


    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public Model getModel() {
        return model;
    }

    public Date getExpiry() {
        return expiry;
    }
}
