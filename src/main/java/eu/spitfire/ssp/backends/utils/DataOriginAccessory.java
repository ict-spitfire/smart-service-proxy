package eu.spitfire.ssp.backends.utils;

import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 23.09.13
 * Time: 21:42
 * To change this template use File | Settings | File Templates.
 */
public interface DataOriginAccessory<T> {

    public void processHttpRequest(SettableFuture<DataOriginResponseMessage> dataOriginResponseFuture,
                                   HttpRequest httpRequest, T dataOrigin);

//    /**
//     * Reads the complete model, i.e. the model with all resources from the given data origin. As there is no return
//     * value implementing classes MUST set the given future with either a value (instance of {@link ExpiringModel}
//     * or an {@link Exception}.
//     *
//     * @param modelFromDataOriginFuture the {@link SettableFuture} to be set with an {@link ExpiringModel} containing
//     *                                  all resources from the given data origin and their expiry
//     * @param dataOrigin the data origin to read the model from
//     */
//    public abstract void readModel(SettableFuture<ExpiringModel> modelFromDataOriginFuture, T dataOrigin);
//
//    /**
//     * Sets the given data origin to contain the states of the one or more resources given as newModel
//     *
//     * @param httpResponseStatusFuture the {@link SettableFuture} to be set with the {@link HttpResponseStatus} to be
//     *                                 sent to the HTTP client to indicate the result of the operation
//     * @param dataOrigin the data origin to be updated
//     * @param newModel the new model to overwrite the existing model at the data origin
//     */
//    public abstract void setModel(SettableFuture<HttpResponseStatus> httpResponseStatusFuture, T dataOrigin,
//                                  Model newModel);
//
//    /**
//     * Updates the model at the given data origin using the given update message. This method is called by the
//     * framework if there is arbitrary data to be sent to the data origin to update the model in a data origin specific
//     * way.
//     *
//     * @param httpResponseStatusFuture the {@link SettableFuture} to be set with the {@link HttpResponseStatus} to be
//     *                                 sent to the HTTP client to indicate the result of the operation
//     * @param dataOrigin the data origin to be updated
//     * @param updateMessage arbitrary message to be processed by the data origin to update the model from the data
//     *                      origin
//     */
//    public abstract void updateModel(SettableFuture<HttpResponseStatus> httpResponseStatusFuture, T dataOrigin,
//                                     String updateMessage);
//
//
//    /**
//     * Deletes the given model from the data origin
//     * @param httpResponseStatusFuture the {@link SettableFuture} to be set with the {@link HttpResponseStatus} to be
//     *                                 sent to the HTTP client to indicate the result of the operation
//     * @param dataOrigin the data origin to delete the model from
//     */
//    public abstract void deleteModel(SettableFuture<HttpResponseStatus> httpResponseStatusFuture, T dataOrigin);

//    public abstract R convertHttpRequest(HttpRequest httpRequest, URI resourceUri);
}
