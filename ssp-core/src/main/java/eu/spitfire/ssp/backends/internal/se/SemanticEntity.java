package eu.spitfire.ssp.backends.internal.se;

import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;

/**
 * Created by olli on 07.07.14.
 */
public class SemanticEntity extends DataOrigin<URI> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    protected SemanticEntity(URI identifier) {
        super(identifier, identifier);
    }


    /**
     * Instances of {@link eu.spitfire.ssp.backends.internal.se.SemanticEntity} are <b>not</b> observable.
     *
     * @return <code>false</code>
     */
    @Override
    public boolean isObservable() {
        return false;
    }


    @Override
    public int hashCode() {
        return this.getGraphName().hashCode();
    }


    @Override
    public boolean equals(Object object) {
        if(object == null || !(object instanceof SemanticEntity))
            return false;

        SemanticEntity other = (SemanticEntity) object;
        return other.getGraphName().equals(this.getGraphName());
    }
}
