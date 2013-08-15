package eu.spitfire.ssp.core.pipeline.handler.cache;

import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.core.pipeline.messages.ResourceStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 07.08.13
 * Time: 19:37
 * To change this template use File | Settings | File Templates.
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

        return new ResourceStatusMessage(resourceUri, cachedResource, expiry);
    }

    @Override
    public synchronized void putResourceToCache(final URI resourceUri, Model model, Date expiry) {

        //Stop deletion of resource status from cache (if any)
        if(expiryTasks.containsKey(resourceUri)){
            if(expiryTasks.get(resourceUri).cancel(false))
                log.debug("Deletion of cached resource status of {} canceled.", resourceUri);
            else
                log.warn("Failed to cancel deletion of cached resource status of {}.", resourceUri);
        }

        //Put fresh status into cache and schedule deletion on expiry
        cache.put(resourceUri, model);
        expiries.put(resourceUri, expiry);
        log.info("Put fresh status of {} into cache (expiry: {}).", resourceUri, expiry);


        expiryTasks.put(resourceUri, executorService.schedule(new Runnable(){

            @Override
            public void run() {
                deleteResource(resourceUri);
                log.debug("Deleted status for resource {} from cache.", resourceUri);
            }
        }, expiry.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public synchronized void deleteResource(URI resourceUri) {
        cache.remove(resourceUri);
        expiries.remove(resourceUri);
    }
}
