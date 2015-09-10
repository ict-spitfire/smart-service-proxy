package eu.spitfire.ssp.server.internal.message;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.generic.DataOrigin;

/**
 * Internal message to replace one {@link eu.spitfire.ssp.backend.generic.DataOrigin} with another
 *
 * @author Oliver Kleine
 */
public class DataOriginReplacementRequest<I, D extends DataOrigin<I>> {

    private final D oldDataOrigin;
    private final D newDataOrigin;
    private SettableFuture<Void> replacementFuture;

    /**
     * Creates a new {@link eu.spitfire.ssp.server.internal.message.DataOriginReplacementRequest}.
     * @param oldDataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to be replaced
     * @param newDataOrigin the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to replace the old one
     */
    public DataOriginReplacementRequest(D oldDataOrigin, D newDataOrigin){
        this.oldDataOrigin = oldDataOrigin;
        this.newDataOrigin = newDataOrigin;
        this.replacementFuture = SettableFuture.create();
    }

    /**
     * Returns the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to be replaced
     * @return the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to be replaced
     */
    public D getOldDataOrigin() {
        return oldDataOrigin;
    }

    /**
     * Returns the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to replace the old one
     * @return the {@link eu.spitfire.ssp.backend.generic.DataOrigin} to replace the old one
     */
    public D getNewDataOrigin() {
        return newDataOrigin;
    }

    /**
     * Returns the {@link com.google.common.util.concurrent.SettableFuture} to be set with the result of the replacement
     * process, i.e. with <code>null</code> if successful or with an {@link java.lang.Exception} in case of an error.
     *
     * @return the {@link com.google.common.util.concurrent.SettableFuture} to be set with the result of the replacement
     * process.
     */
    public SettableFuture<Void> getReplacementFuture() {
        return replacementFuture;
    }
}
