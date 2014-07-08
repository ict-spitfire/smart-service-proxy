package eu.spitfire.ssp.backends.external.n3files;

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

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backends.generic.DataOrigin}
     * @param hostName the host name of the SSP the N3 file is located on (according to the ssp.properties)
     */
    public N3File(Path identifier, String hostName) throws URISyntaxException{
        super(
                identifier.isAbsolute() ? identifier : identifier.toAbsolutePath().normalize(),
                new URI("file", null, hostName, -1, identifier.toString(), null, null)
        );
    }

    /**
     * Returns <code>true</code> (because files are observable)
     * @return <code>true</code>
     */
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
        if(object == null || !(object instanceof N3File))
            return false;

        N3File other = (N3File) object;

        return this.getGraphName().equals(other.getGraphName()) && this.getIdentifier().equals(other.getIdentifier());
    }
}
