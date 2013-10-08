package eu.spitfire.ssp.backends.generic.messages;

import com.hp.hpl.jena.rdf.model.Model;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.Date;

/**
 * A {@link InternalDataOriginResponseMessage} represents the response from a data origin upon request processing. It is used
 * by the framework to create a proper HTTP response for the HTTP client that sent the HTTP request that was processed.
 *
 * @author Oliver Kleine
 */
public class InternalDataOriginResponseMessage {

    private final HttpResponseStatus httpResponseStatus;
    private final Model model;
    private final Date expiry;

    /**
     * Constructor for error responses or any other responses without upload, e.g. upon resource deletion or update
     *
     * @param httpResponseStatus the {@link HttpResponseStatus} of the response to be sent
     */
    public InternalDataOriginResponseMessage(HttpResponseStatus httpResponseStatus){
        this(httpResponseStatus, null, null);
    }

    /**
     * Constructor to be used upon successful status retrieval from the data origin
     *
     * @param httpResponseStatus the {@link HttpResponseStatus} of the response to be sent
     * @param model the model returned by the data origin (may contain several resources)
     * @param expiry the expiry date of the model
     */
    public InternalDataOriginResponseMessage(HttpResponseStatus httpResponseStatus, Model model, Date expiry){
        this.httpResponseStatus = httpResponseStatus;
        this.model = model;
        this.expiry = expiry;
    }

    /**
     * Returns the {@link HttpResponseStatus}
     * @return  the {@link HttpResponseStatus}
     */
    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    /**
     * Returns the de-serialized {@link Model} from the data origin
     * @return the de-serialized {@link Model} from the data origin
     */
    public Model getModel() {
        return model;
    }

    /**
     * Returns the expiry of the model
     * @return the expiry of the model
     */
    public Date getExpiry() {
        return expiry;
    }
}
