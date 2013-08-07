package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 07.08.13
 * Time: 19:37
 * To change this template use File | Settings | File Templates.
 */
public class SimpleSemanticCache extends AbstractSemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private ConcurrentHashMap<URI, Model> cache = new ConcurrentHashMap<>();

    @Override
    public Model findCachedResource(URI resourceUri) {
        return cache.get(resourceUri);
    }

    @Override
    public void putResourceToCache(URI resourceUri, Model model) {
        cache.put(resourceUri, model);
    }


}
