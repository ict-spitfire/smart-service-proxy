package eu.spitfire.ssp.backends.generic;

import java.net.URI;

/**
 * A {@link eu.spitfire.ssp.backends.generic.DataOrigin} is an abstraction for an arbitrary component hosting semantic
 * data. This can e.g. be a Webservice or a local file.
 *
 * The generic {@link T} represents the type of the identifier for this data origin. For a data origin being a
 * Webservice this could e.g. be {@link URI}. Then {@link #getIdentifier()} returns a {@link java.net.URI} identifying
 * this Webservice.
 *
 * The complete RDF information provided by an instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin} is
 * represented as a named graph. This is to give the whole set of triples provided by a single data origin an unique
 * name, e.g. to limit the amount of data to process a SPARQL query on.
 *
 * @author Oliver Kleine
 */
public abstract class DataOrigin<T> {

    private T identifier;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    protected DataOrigin(T identifier){
        this.identifier = identifier;
    }

    /**
     * Returns <code>true</code> if this {@link eu.spitfire.ssp.backends.generic.DataOrigin} instance is observable
     * and <code>false</code> otherwise
     * @return <code>true</code> if this {@link eu.spitfire.ssp.backends.generic.DataOrigin} instance is observable
     * and <code>false</code> otherwise
     */
    public abstract boolean isObservable();


    /**
     * Returns the identifier of this data origin (e.g. a path for n3files or a URI for Webservices). The returned value
     * is unique among all {@link eu.spitfire.ssp.backends.generic.DataOrigin}s per local backend.
     *
     * @return the identifier of this data origin
     */
    public T getIdentifier(){
        return this.identifier;
    }


    /**
     * Returns the graph backendName of the model hosted by this data origin. The graph backendName is used to globally identify the
     * data that was retrieved by a {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. a graph backendName is unique
     * over all {@link eu.spitfire.ssp.backends.generic.DataOrigin}s "worldwide" and not only locally as the result
     * {@link #getIdentifier()} is.
     *
     * @return the graph backendName of the model hosted by this data origin
     */
    public abstract URI getGraphName();

    @Override
    public abstract int hashCode();

    /**
     * Returns <code>true</code> if the given {@link java.lang.Object} is not <code>null</code> and is an instance of
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} and the {@link java.net.URI}s returned by
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin#getGraphName()} of this and the given instance are equal.
     * *
     * @param object the {@link java.lang.Object} to check this instance of
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} for equality.
     *
     * @return <code>true</code> if this and the given instance equal, <code>false</code> otherwise.
     */
    @Override
    public abstract boolean equals(Object object);

    public String toString(){
        return "[Identifier: " + identifier + ", Graph Name: " + getGraphName() + "]";
    }
}
