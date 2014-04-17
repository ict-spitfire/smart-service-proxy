package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Created by olli on 14.04.14.
 */
public class FileDataOrigin extends DataOrigin<Path> {

    private URI graphName;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
//     * @param graphName  the backendName of the graph representing the semantic information at this
//     *                   {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     */
    public FileDataOrigin(Path identifier, String sspHostName) throws URISyntaxException {
        super(identifier);
        this.graphName = new URI("file", null, sspHostName, -1, identifier.toString(), null, null);
    }

    @Override
    public boolean isObservable() {
        return true;
    }

    @Override
    public URI getGraphName() {
        return this.graphName;
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
