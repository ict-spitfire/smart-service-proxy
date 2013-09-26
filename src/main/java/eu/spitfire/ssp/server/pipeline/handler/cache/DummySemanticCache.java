package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;

/**
 * A very simple implementation of a semantic cache to provide cached states of semantic resources. The caching
 * just happens in a {@link HashMap} mapping {@link URI}s to {@link Model}s.
 *
 * After expiry the states are deleted from the map automatically, such that requests after expiry cannot be answered
 * with a state from the cache.
 *
 * @author Oliver Kleine
 */
public class DummySemanticCache extends SemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        return null;
    }

    @Override
    public synchronized void putResourceToCache(final URI resourceUri, Model resourceStatus, Date expiry) {
        //Nothing to do...
    }

    @Override
    public synchronized void deleteResource(URI resourceUri) {
        //Nothing to do...
    }

    @Override
    public void updateStatement(Statement statement) {
        //Nothing to do...
    }

    @Override
    public boolean supportsSPARQL() {
        return false;
    }
}
