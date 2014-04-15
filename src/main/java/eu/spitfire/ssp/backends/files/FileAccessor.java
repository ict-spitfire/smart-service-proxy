package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.*;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessException;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.utils.Language;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
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
    public ListenableFuture<WrappedDataOriginStatus> getStatus(Path identifier){
        SettableFuture<WrappedDataOriginStatus> statusFuture = SettableFuture.create();

        try{
            BufferedReader fileReader = new BufferedReader(new FileReader(identifier.toString()));
            String tmpGraphName = null;
            String tmpExpiry = null;

            for(int i = 0; i < 2; i++){
                String actualLine = fileReader.readLine();

                if(actualLine != null && actualLine.startsWith("#graphname: ")){
                    tmpGraphName = actualLine.substring(actualLine.indexOf(": ") + 2);
                    log.info("Name of graph from file \"{}\" is \"{}\"", identifier, tmpGraphName);
                }

                else if(actualLine != null && actualLine.startsWith("expires: ")){
                    tmpExpiry = actualLine.substring(actualLine.indexOf(": ") + 2);
                    log.info("Graph from file \"{}\" expires on {}", identifier, tmpExpiry);
                }
            }

            fileReader = new BufferedReader(new FileReader(identifier.toString()));

            Model model = ModelFactory.createDefaultModel();
            model.read(fileReader, null, Language.RDF_N3.lang);

            URI graphName = tmpGraphName == null ? null : new URI(tmpGraphName);

            //TODO: Read Expiry Date!
            Date expiry = new Date(System.currentTimeMillis() + MILLIS_PER_YEAR);

            FileDataOrigin dataOrigin = new FileDataOrigin(graphName, identifier);

            WrappedDataOriginStatus dataOriginStatus = new WrappedDataOriginStatus(WrappedDataOriginStatus.Code.OK,
                    dataOrigin.getGraphName(), model, expiry);

            statusFuture.set(dataOriginStatus);
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
}
