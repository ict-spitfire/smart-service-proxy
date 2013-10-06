package eu.spitfire.ssp.backends.generic.messages;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

/**
 * Instances of {@link InternalResourceStatusMessage} contain the {@link URI} to identify the resource, a {@link Model}
 * containing the status of the resource and a {@link Date} to define the expiry of the actual status.
 *
 * @author Oliver Kleine
 */
public class InternalResourceStatusMessage {

    //public static final long MILLIS_PER_100_YEARS = 3153600073000L;

    private URI resourceUri;
    private final Model model;
    private final Date expiry;

    public InternalResourceStatusMessage(Model model) throws MultipleSubjectsInModelException, URISyntaxException {
        this(model, null);
    }

    public InternalResourceStatusMessage(Model model, Date expiry) throws MultipleSubjectsInModelException,
                                                                          URISyntaxException {

        //We allow only allow models with at most one subject
        if(!model.isEmpty()){
            ResIterator subjectIterator = model.listSubjects();
            if(subjectIterator.hasNext()){
                Resource subject = subjectIterator.nextResource();
                resourceUri = new URI(subject.toString());
                if(subjectIterator.hasNext())
                    throw new MultipleSubjectsInModelException(model.listSubjects());
            }
        }

        this.model = model;
        this.expiry = expiry;
    }

    /**
     * Returns the {@link Model} containing the actual status of the resource
     * @return the {@link Model} containing the actual status of the resource
     */
    public Model getModel() {
        return this.model;
    }

    /**
     * Returns the expiry of the actual status
     * @return the expiry of the actual status
     */
    public Date getExpiry() {
        return this.expiry;
    }

    /**
     * Returns the {@link URI} identifying the resource
     * @return the {@link URI} identifying the resource
     */
    public URI getResourceUri() {
        return this.resourceUri;
    }

    @Override
    public String toString(){
        return "[Resource status message] " + getResourceUri() + " (URI), " + getExpiry() + " (expiry)";
    }
}
