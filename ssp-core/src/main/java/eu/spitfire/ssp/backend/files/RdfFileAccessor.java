package eu.spitfire.ssp.backend.files;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.generic.DataOriginAccessor;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.utils.Language;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;


/**
 * A {@link RdfFileAccessor} is the component to read RDF data from local turtle files.
 *
 * @author Oliver Kleine
 */
public class RdfFileAccessor extends DataOriginAccessor<Path, RdfFile> {

    private static Logger LOG = LoggerFactory.getLogger(RdfFileAccessor.class.getName());

    public RdfFileAccessor(RdfFilesBackendComponentFactory componentFactory) {
        super(componentFactory);
    }


    @Override
    public ListenableFuture<ExpiringNamedGraph> getStatus(RdfFile dataOrigin){
        SettableFuture<ExpiringNamedGraph> statusFuture = SettableFuture.create();

        try{
            File file = new File(dataOrigin.getIdentifier().toString());
            if(file.length() == 0){
                statusFuture.set(new ExpiringNamedGraph(
                        dataOrigin.getGraphName(), ModelFactory.createDefaultModel()
                ));
            }
            else{
                BufferedReader fileReader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(new File(dataOrigin.getIdentifier().toString())), "UTF-8"
                ));

                Model model = ModelFactory.createDefaultModel();
                if(dataOrigin.getIdentifier().toString().endsWith(".ttl")) {
                    model.read(fileReader, null, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
                } else if(dataOrigin.getIdentifier().toString().endsWith(".rdf")){
                    model.read(fileReader, null, Language.RDF_XML.getRdfFormat().getLang().getName());
                }

//                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                model.write(outputStream);
//
//                LOG.error("\n{}", outputStream.toString("UTF-8"));

                //URI graphName = new URI("file", null, sspHost, -1, dataOrigin.getIdentifier().toString(), null, null);

                ExpiringNamedGraph expiringNamedGraph = new ExpiringNamedGraph(dataOrigin.getGraphName(), model);
                statusFuture.set(expiringNamedGraph);
            }
        }

        catch (Exception ex) {
            LOG.error("Exception while getting status from {}", dataOrigin.getIdentifier(), ex);
            statusFuture.setException(ex);
        }

        return statusFuture;
    }
}
