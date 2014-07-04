package eu.spitfire.ssp.backends.n3files;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.server.common.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.utils.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;


/**
 * A {@link N3FileAccessor} is the component to read RDF data from local N3 n3files.
 *
 * @author Oliver Kleine
 */
public class N3FileAccessor extends Accessor<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String sspHostName;

    public N3FileAccessor(N3FileBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.sspHostName = this.getComponentFactory().getSspHostName();
    }


    @Override
    public ListenableFuture<GraphStatusMessage> getStatus(DataOrigin<Path> dataOrigin){
        SettableFuture<GraphStatusMessage> statusFuture = SettableFuture.create();

        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(dataOrigin.getIdentifier().toString()));

            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            URI graphName = new URI("file", null, sspHostName, -1, dataOrigin.getIdentifier().toString(), null, null);

            ExpiringNamedGraph dataOriginStatus = new ExpiringNamedGraph(graphName, model);
            statusFuture.set(new ExpiringNamedGraphStatusMessage(dataOriginStatus));
        }

        catch (Exception ex) {
            log.error("Exception while getting status from {}", dataOrigin.getIdentifier(), ex);
            statusFuture.setException(ex);
        }

        return statusFuture;
    }
}
