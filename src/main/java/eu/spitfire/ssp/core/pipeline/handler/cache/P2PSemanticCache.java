package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

//TODO Sandro
public class P2PSemanticCache extends AbstractSemanticCache{
    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void putResourceToCache(URI resourceUri, Model model, Date expiry) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void deleteResource(URI resourceUri) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
