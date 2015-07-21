package eu.spitfire.ssp.backends.external.turtlefiles;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginInquiryResult;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.server.internal.messages.responses.ExpiringNamedGraph;
import eu.spitfire.ssp.utils.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;


/**
 * A {@link TurtleFileAccessor} is the component to read RDF data from local turtle files.
 *
 * @author Oliver Kleine
 */
public class TurtleFileAccessor extends Accessor<Path, TurtleFile> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String sspHost;

    public TurtleFileAccessor(TurtleFileBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.sspHost = this.getComponentFactory().getSspHostName();
    }


    @Override
    public ListenableFuture<DataOriginInquiryResult> getStatus(TurtleFile dataOrigin){
        SettableFuture<DataOriginInquiryResult> statusFuture = SettableFuture.create();

        try{
            File file = new File(dataOrigin.getIdentifier().toString());
            if(file.length() == 0){
                statusFuture.set(new DataOriginAccessError(AccessResult.Code.INTERNAL_ERROR, "File was empty!"));
            }
            else{
                BufferedReader fileReader = new BufferedReader(new FileReader(dataOrigin.getIdentifier().toString()));

                Model model = ModelFactory.createDefaultModel();
                model.read(fileReader, null, Language.RDF_TURTLE.lang);

                URI graphName = new URI("file", null, sspHost, -1, dataOrigin.getIdentifier().toString(), null, null);

                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(graphName, model);
                statusFuture.set(expiringNamedGraph);
            }
        }

        catch (Exception ex) {
            log.error("Exception while getting status from {}", dataOrigin.getIdentifier(), ex);
            statusFuture.setException(ex);
        }

        return statusFuture;
    }
}
