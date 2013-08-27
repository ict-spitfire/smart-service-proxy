package eu.spitfire.ssp.server.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.server.pipeline.messages.ResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A very simple implementation of a semantic cache to provide cached states of semantic resources. The caching
 * just happens in a {@link HashMap} mapping {@link URI}s to {@link Model}s.
 *
 * After expiry the states are deleted from the map automatically, such that requests after expiry cannot be answered
 * with a state from the cache.
 *
 * @author Oliver Kleine
 */
public class SimpleSemanticCache extends AbstractSemanticCache {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private Map<URI, Model> cache = new HashMap<>();
    private Map<URI, Date> expiries = new HashMap<>();

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private ConcurrentHashMap<URI, ScheduledFuture> expiryTasks = new ConcurrentHashMap<>();


    @Override
    public ResourceStatusMessage getCachedResource(URI resourceUri) {
        log.debug("Lookup cached resource {}", resourceUri);

        Model cachedResource = cache.get(resourceUri);

        if(cachedResource == null)
            return null;

        Date expiry = expiries.get(resourceUri);
        if(expiry == null || expiry.before(new Date()))
            return null;

        log.info("Found valid resource status for {}.", resourceUri);

        return new ResourceStatusMessage(resourceUri, cachedResource, expiry);
    }

    @Override
    public synchronized void putResourceToCache(final URI resourceUri, Model resourceStatus, Date expiry) {

        //Stop deletion of resource status from cache (if any)
        if(expiryTasks.containsKey(resourceUri)){
            if(expiryTasks.get(resourceUri).cancel(false))
                log.debug("Deletion of cached resource status of {} canceled.", resourceUri);
            else
                log.warn("Failed to cancel deletion of cached resource status of {}.", resourceUri);
        }

        //Put fresh status into cache and schedule deletion on expiry
        cache.put(resourceUri, resourceStatus);
        expiries.put(resourceUri, expiry);
        log.info("Put fresh status of {} into cache (expiry: {}).", resourceUri, expiry);


        expiryTasks.put(resourceUri, executorService.schedule(new Runnable(){

            @Override
            public void run() {
                deleteResource(resourceUri);
                log.info("Deleted status for resource {} from cache.", resourceUri);
            }
        }, expiry.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public synchronized void deleteResource(URI resourceUri) {
        cache.remove(resourceUri);
        expiries.remove(resourceUri);
    }
}
