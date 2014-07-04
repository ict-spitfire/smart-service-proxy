package eu.spitfire.ssp.backends.virtualsensors;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;

/**
 * Created by olli on 06.05.14.
 */
public class VirtualSensor extends DataOrigin<URI>{

    private Query sparqlQuery;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public VirtualSensor(URI identifier, Query sparqlQuery){
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

    /**
     * Returns the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     * @return the {@link com.hp.hpl.jena.query.Query} which is used to retrieve the actual sensor value.
     */
    public Query getSparqlQuery() {
        return this.sparqlQuery;
    }

    @Override
    public int hashCode() {
        return this.getGraphName().hashCode();
    }


    @Override
    public boolean equals(Object object) {
        return false;
    }


}
