package eu.spitfire.ssp.backends;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * A {@link DataOriginAccessory} is responsible to process an incoming {@link HttpRequest} for a
 * particular resource on the given data origin.
 *
 * @author Oliver Kleine
 */
public interface DataOriginAccessory<T> {

    /**
     * Process the given {@link HttpRequest} on the given data origin and set the given {@link SettableFuture} with a
     * {@link DataOriginResponseMessage}
     *
     * @param dataOriginResponseFuture the future to be set with the result of the operation
     * @param httpRequest the {@link HttpRequest} defining the operation to be processed
     * @param dataOrigin  the data origin the operation is to be processed on
     */
    public void processHttpRequest(SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture,
                                   HttpRequest httpRequest, T dataOrigin);
}
