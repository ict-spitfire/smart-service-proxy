package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;

import java.net.URI;
import java.util.Date;

//TODO Sandro
public class P2PSemanticCache extends AbstractSemanticCache{

    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
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
}
