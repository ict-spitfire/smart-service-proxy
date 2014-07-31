package eu.spitfire.ssp.server.internal.messages.requests;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.DataOrigin;

/**
 * Created by olli on 16.04.14.
 */
public class DataOriginDeregistration<I, D extends DataOrigin<I>> {

    private D dataOrigin;
    private SettableFuture<Void> deregistrationFuture;

    public DataOriginDeregistration(D dataOrigin, SettableFuture<Void> deregistrationFuture) {
        this.dataOrigin = dataOrigin;
        this.deregistrationFuture = deregistrationFuture;
    }

    public D getDataOrigin() {
        return dataOrigin;
    }

    public SettableFuture<Void> getDeregistrationFuture() {
        return deregistrationFuture;
    }
}
