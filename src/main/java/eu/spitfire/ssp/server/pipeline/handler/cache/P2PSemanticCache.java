package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.server.pipeline.messages.ResourceResponseMessage;

import java.net.URI;
import java.util.Date;

//TODO Sandro
public class P2PSemanticCache extends SemanticCache {

    @Override
    public ResourceResponseMessage getCachedResource(URI resourceUri) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void putResourceToCache(URI resourceUri, Model resourceStatus, Date expiry) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void deleteResource(URI resourceUri) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void updateStatement(Statement statement) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean supportsSPARQL() {
        return true;
    }
}
