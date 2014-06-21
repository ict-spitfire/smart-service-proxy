package eu.spitfire.ssp.backends.slse;

import com.hp.hpl.jena.query.Query;
import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class SlseDataOrigin extends DataOrigin<URI>{

    private final Query sparqlQuery;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public SlseDataOrigin(URI identifier, Query sparqlQuery) {
        super(identifier);
        this.sparqlQuery = sparqlQuery;
    }


    @Override
    public boolean isObservable() {
        return true;
    }


    @Override
    public URI getGraphName() {
        return super.getIdentifier();
    }


    @Override
    public int hashCode() {
        return this.getGraphName().hashCode();
    }


    @Override
    public boolean equals(Object object) {
        return false;
    }

    public Query getSparqlQuery() {
        return sparqlQuery;
    }
}
