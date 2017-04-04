package eu.spitfire.ssp.backend.files;

import eu.spitfire.ssp.backend.generic.DataOrigin;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * A {@link RdfFile} represents a local file that contains RDF data in the form of Turtle.
 *
 * @author Oliver Kleine
 */
public class RdfFile extends DataOrigin<Path> {

    /**
     * Creates a new instance of {@link eu.spitfire.ssp.backend.generic.DataOrigin}
     *
     * @param identifier the identifier for this {@link eu.spitfire.ssp.backend.generic.DataOrigin}
     * @param hostName the host name of the SSP the Turtle file is located on (according to the ssp.properties)
     */
    public RdfFile(Path rootDirectory, Path identifier, String hostName, int port) throws URISyntaxException{
        super(
                identifier.isAbsolute() ? identifier : identifier.toAbsolutePath().normalize(),
                new URI("http", null, hostName, port, "/" + rootDirectory.relativize(identifier).toString(), null, null)
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
        if(object == null || !(object instanceof RdfFile))
            return false;

        RdfFile other = (RdfFile) object;

        return this.getGraphName().equals(other.getGraphName()) && this.getIdentifier().equals(other.getIdentifier());
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
