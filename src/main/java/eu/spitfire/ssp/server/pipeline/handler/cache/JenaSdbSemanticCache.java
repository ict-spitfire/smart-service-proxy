package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sdb.SDBFactory;
import com.hp.hpl.jena.sdb.Store;
import com.hp.hpl.jena.sdb.StoreDesc;
import com.hp.hpl.jena.sdb.sql.JDBC;
import com.hp.hpl.jena.sdb.sql.SDBConnection;
import com.hp.hpl.jena.sdb.store.DatabaseType;
import com.hp.hpl.jena.sdb.store.LayoutType;
import com.hp.hpl.jena.sdb.util.StoreUtils;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;

/**
 * A semantic cache which is backed by a triple-store based on Jena SDB in a mySQL database. The database
 * is suitable to process SPARQL queries.
 *
 * Note: The database (scheme) given as part of the JDBC-URL in ssp.properties must already exist!
 *
 * @author Oliver Kleine
 */
public class JenaSdbSemanticCache extends AbstractSemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private StoreDesc storeDescription;
    private SDBConnection sdbConnection;

    public JenaSdbSemanticCache(String jdbcUrl, String user, String password) throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        JDBC.loadDriverMySQL();

        storeDescription = new StoreDesc(LayoutType.LayoutTripleNodesHash, DatabaseType.MySQL);
        sdbConnection = new SDBConnection(jdbcUrl, user, password);

        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);

        if(!StoreUtils.isFormatted(store))
            store.getTableFormatter().create();

        store.getTableFormatter().truncate();
    }

    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model model = SDBFactory.connectNamedModel(store, resourceUri.toString());

        store.close();
        if(model.listSubjects().hasNext()){
            log.info("Resource {} found in cache.", resourceUri);
            return new ResourceStatusMessage(resourceUri, model, new Date(System.currentTimeMillis() + 100000));
        }
        else{
            log.info("Resource {} NOT found in cache.", resourceUri);
            return null;
        }


    }

    @Override
    public void putResourceToCache(URI resourceUri, Model resourceStatus, Date expiry) {
        deleteResource(resourceUri);

        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model sdbModel = SDBFactory.connectNamedModel(store, resourceUri.toString());
        sdbModel.add(resourceStatus);

        store.close();
    }

    @Override
    public void deleteResource(URI resourceUri) {
        Store store = SDBFactory.connectStore(sdbConnection, storeDescription);
        Model sdbModel = SDBFactory.connectNamedModel(store, resourceUri.toString());
        sdbModel.removeAll();

        store.close();
    }
}
