package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;
import java.nio.file.Path;

/**
 * Created by olli on 10.04.14.
 */
public class FilesDataOrigin extends DataOrigin<Path> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param graphName  the name of the graph representing the semantic information at this
     *                   {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    protected FilesDataOrigin(URI graphName, Path identifier) {
        super(graphName, identifier);
    }


    @Override
    public boolean isObservable() {
        return true;
    }


    @Override
    public int hashCode() {
        return this.getGraphName().hashCode() + this.getIdentifier().hashCode();
    }


    @Override
    public boolean equals(Object object) {
        if(object == null || !(object instanceof DataOrigin))
            return false;

        DataOrigin other = (DataOrigin) object;
        return this.getGraphName().equals(other.getGraphName()) && this.getIdentifier().equals(other.getIdentifier());
    }
}
