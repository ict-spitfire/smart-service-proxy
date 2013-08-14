package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;

import java.net.URI;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 12.08.13
 * Time: 12:08
 * To change this template use File | Settings | File Templates.
 */
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
