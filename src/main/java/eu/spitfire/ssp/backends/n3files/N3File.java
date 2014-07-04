package eu.spitfire.ssp.backends.n3files;

import eu.spitfire.ssp.backends.generic.DataOrigin;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * A {@link N3File} represents a local file that contains RDF data in the
 * form of N3.
 *
 * @author Oliver Kleine
 */
public class N3File extends DataOrigin<Path> {

    private URI graphName;

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param sspHostName the host name of the SSP the N3 file is located on (according to the ssp.properties)
     */
    public N3File(Path identifier, String sspHostName) throws URISyntaxException {
        super(identifier.isAbsolute() ? identifier : identifier.toAbsolutePath().normalize());
        this.graphName = new URI("file", null, sspHostName, -1, this.getIdentifier().toString(), null, null);
    }

    /**
     * Returns <code>true</code> (because files are observable)
     * @return <code>true</code>
     */
    @Override
    public boolean isObservable() {
        return true;
    }

    /**
     * Returns the name of the graph that contains the data from the N3 file.
     * <br><br>
     * <code>file://<ssp-hostname>/<identifier></code>
     *
     * @return the name of the graph that contains the data from the N3 file.
     */
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
        if(object == null || !(object instanceof N3File))
            return false;

        N3File other = (N3File) object;

        return this.getGraphName().equals(other.getGraphName()) && this.getIdentifier().equals(other.getIdentifier());
    }
}
