package eu.spitfire.ssp.backends.external.n3files;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.DataOriginAccessResult;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.server.internal.messages.ExpiringNamedGraph;
import eu.spitfire.ssp.utils.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;


/**
 * A {@link N3FileAccessor} is the component to read RDF data from local N3 n3files.
 *
 * @author Oliver Kleine
 */
public class N3FileAccessor extends Accessor<Path, N3File> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String sspHostName;

    public N3FileAccessor(N3FileBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.sspHostName = this.getComponentFactory().getSspHostName();
    }


    @Override
    public ListenableFuture<DataOriginAccessResult> getStatus(N3File dataOrigin){
        SettableFuture<DataOriginAccessResult> statusFuture = SettableFuture.create();

        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(dataOrigin.getIdentifier().toString()));

            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            URI graphName = new URI("file", null, sspHostName, -1, dataOrigin.getIdentifier().toString(), null, null);

            ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, model);
            statusFuture.set(expiringNamedGraph);
        }

        catch (Exception ex) {
            log.error("Exception while getting status from {}", dataOrigin.getIdentifier(), ex);
            statusFuture.setException(ex);
        }

        return statusFuture;
    }
}
