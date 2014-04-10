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
 * Each {@link eu.spitfire.ssp.backends.generic.DataOrigin} instance is connected to a graph name. In this context a
 * graph is the component to contain the complete semantic information provided by this
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * @author Oliver Kleine
 */
public abstract class DataOrigin<T> {

    private URI graphName;
    private T identifier;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param graphName the name of the graph representing the semantic information at this
     *                  {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    protected DataOrigin(URI graphName, T identifier){
        this.graphName = graphName;
        this.identifier = identifier;
    }

    /**
     * Returns <code>true</code> if this {@link eu.spitfire.ssp.backends.generic.DataOrigin} instance is observable
     * and <code>false</code> otherwise
     * @return <code>true</code> if this {@link eu.spitfire.ssp.backends.generic.DataOrigin} instance is observable
     * and <code>false</code> otherwise.
     */
    public abstract boolean isObservable();


    /**
     * Returns the identifier of this data origin
     * @return the identifier of this data origin
     */
    public T getIdentifier(){
        return this.identifier;
    }


    /**
     * Returns the graph name of the model hosted by this data origin
     * @return the graph name of the model hosted by this data origin
     */
    public URI getGraphName() {
        return graphName;
    }

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
}
