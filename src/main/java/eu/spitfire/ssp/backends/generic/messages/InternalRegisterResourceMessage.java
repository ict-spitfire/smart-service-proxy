package eu.spitfire.ssp.backends.generic.messages;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;

import java.net.URISyntaxException;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
public class InternalRegisterResourceMessage<T> extends InternalResourceStatusMessage{

    private T dataOrigin;
    private SemanticHttpRequestProcessor httpRequestProcessor;

    public InternalRegisterResourceMessage(SemanticHttpRequestProcessor httpRequestProcessor, T dataOrigin,
                               Model model, Date expiry) throws MultipleSubjectsInModelException, URISyntaxException {
        super(model, expiry);
        this.httpRequestProcessor = httpRequestProcessor;
        this.dataOrigin = dataOrigin;

    }

    public T getDataOrigin(){
        return this.dataOrigin;
    }

    public SemanticHttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }
}
