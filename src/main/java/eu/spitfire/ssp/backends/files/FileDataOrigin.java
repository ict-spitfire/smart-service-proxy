package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;
import java.nio.file.Path;

/**
 * Created by olli on 14.04.14.
 */
public class FileDataOrigin extends DataOrigin<Path> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param graphName  the backendName of the graph representing the semantic information at this
     *                   {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    protected FileDataOrigin(URI graphName, Path identifier) {
        super(graphName, identifier);
    }

    @Override
    public boolean isObservable() {
        return true;
    }

    @Override
    public int hashCode() {
        return this.getIdentifier().hashCode() | this.getGraphName().hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if(object == null || !(object instanceof FileDataOrigin))
            return false;

        FileDataOrigin other = (FileDataOrigin) object;

        return this.getGraphName().equals(other.getGraphName()) && this.getIdentifier().equals(other.getIdentifier());
    }
}
