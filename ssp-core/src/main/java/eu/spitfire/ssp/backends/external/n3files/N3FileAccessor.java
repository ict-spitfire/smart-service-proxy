package eu.spitfire.ssp.backends.external.n3files;

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
 * A {@link N3FileAccessor} is the component to read RDF data from local N3 n3files.
 *
 * @author Oliver Kleine
 */
public class N3FileAccessor extends Accessor<Path, N3File> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String sspHost;

    public N3FileAccessor(N3FileBackendComponentFactory componentFactory) {
        super(componentFactory);
        this.sspHost = this.getComponentFactory().getSspHostName();
    }


    @Override
    public ListenableFuture<DataOriginInquiryResult> getStatus(N3File dataOrigin){
        SettableFuture<DataOriginInquiryResult> statusFuture = SettableFuture.create();

        try{
            File file = new File(dataOrigin.getIdentifier().toString());
            if(file.length() == 0){
                statusFuture.set(new DataOriginAccessError(AccessResult.Code.INTERNAL_ERROR, "File was empty!"));
            }
            else{
                BufferedReader fileReader = new BufferedReader(new FileReader(dataOrigin.getIdentifier().toString()));

                Model model = ModelFactory.createDefaultModel();
                model.read(fileReader, null, Language.RDF_N3.lang);

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
