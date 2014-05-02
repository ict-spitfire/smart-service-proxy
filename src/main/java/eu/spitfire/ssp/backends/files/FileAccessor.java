package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.backends.generic.wrappers.ExpiringNamedGraph;
import eu.spitfire.ssp.server.common.messages.ExpiringNamedGraphStatusMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.utils.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;


/**
 * Created by olli on 13.04.14.
 */
public class FileAccessor extends DataOriginAccessor<Path> {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    public FileAccessor(FilesBackendComponentFactory componentFactory) {
        super(componentFactory);
    }


    @Override
    public ListenableFuture<GraphStatusMessage> getStatus(Path identifier){
        SettableFuture<GraphStatusMessage> statusFuture = SettableFuture.create();

        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(identifier.toString()));

            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            URI graphName = new URI("file", null, ((FilesBackendComponentFactory) componentFactory).getSspHostName(),
                    -1, identifier.toString(), null, null);

            Date expiry = new Date(System.currentTimeMillis() + MILLIS_PER_YEAR);

            ExpiringNamedGraph dataOriginStatus = new ExpiringNamedGraph(graphName, model, expiry);
            statusFuture.set(new ExpiringNamedGraphStatusMessage(dataOriginStatus));
        }

        catch (FileNotFoundException ex) {
            String message = "File \"" + identifier + "\" not found!";
            log.error(message, ex);
            statusFuture.setException(ex);
        }

        catch(Exception ex){
            log.error("This should never happen.", ex);
            statusFuture.setException(ex);
        }

        return statusFuture;
    }

    @Override
    public ListenableFuture<GraphStatusMessage> setStatus(Path identifier, Model status) throws DataOriginAccessException {
        //TODO
        return null;
    }

    @Override
    public ListenableFuture<GraphStatusMessage> deleteDataOrigin(Path identifier) throws DataOriginAccessException {
        //TODO
        return null;
    }
}