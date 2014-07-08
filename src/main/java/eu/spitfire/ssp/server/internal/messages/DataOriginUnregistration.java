package eu.spitfire.ssp.server.internal.messages;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.DataOrigin;

/**
 * Created by olli on 16.04.14.
 */
public class DataOriginUnregistration<I, D extends DataOrigin<I>> {

    private D dataOrigin;
    private SettableFuture<Void> unregistrationFuture;

    public DataOriginUnregistration(D dataOrigin, SettableFuture<Void> unregistrationFuture) {
        this.dataOrigin = dataOrigin;
        this.unregistrationFuture = unregistrationFuture;
    }

    public D getDataOrigin() {
        return dataOrigin;
    }

    public SettableFuture<Void> getUnregistrationFuture() {
        return unregistrationFuture;
    }
}
