package eu.spitfire.ssp.backends.generic.access;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Abstract class to be extended by HTTP Webservice instances running on the Smart Service Proxy to provide semantic
 * data, i.e. semantic resource states.
 *
 * Implementing classes are supposed to process the incoming {@link org.jboss.netty.handler.codec.http.HttpRequest},
 * e.g. by converting it to another protocol, forward the translated request to a data-origin, await the
 * response, and set the given {@link com.google.common.util.concurrent.SettableFuture} with an instance of
 * {@link eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus} based on the response from
 * the data-origin.
 *
 * @author Oliver Kleine
 */
public abstract class HttpSemanticProxyWebservice<T> extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    @Override
    public void processHttpRequest(final Channel channel, final HttpRequest httpRequest,
                                   final InetSocketAddress clientAddress){

        try{
            URI proxyUri = new URI(httpRequest.getUri());

            //Look up the data origin associated with the URI contained in the HTTP request
            DataOrigin<T> dataOrigin = getDataOrigin(proxyUri);
            if(dataOrigin == null){
                String message = String.format("Data origin for proxy URI %s not found.", proxyUri.toString());
                throw new DataOriginAccessException(HttpResponseStatus.NOT_FOUND, message);
            }
            log.debug("Found data origin for proxy URI {} (identifier: \"{}\")", proxyUri, dataOrigin.getIdentifier());

            //Look up appropriate accessor for proxy URI
            DataOriginAccessor<T> dataOriginAccessor = getDataOriginAccessor(dataOrigin);
            if(dataOriginAccessor == null){
                String message = String.format("No data origin accessor found for data origin with identifier %s",
                        dataOrigin.getIdentifier().toString());
                throw new DataOriginAccessException(HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
            }

            //Forward message to data origin accessor
            boolean removeOnFailure = removeDataOriginOnAccessFailure(dataOrigin);
            ListenableFuture<Void> resultFuture =
                    dataOriginAccessor.retrieveStatus(channel, clientAddress, dataOrigin, removeOnFailure);

            Futures.addCallback(resultFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void aVoid) {
                    //Nothing to do!
                }

                @Override
                public void onFailure(Throwable throwable) {
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());

                    ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            });
        }

        catch(DataOriginAccessException ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                   ex.getHttpResponseStatus(), ex);

            ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
            future.addListener(ChannelFutureListener.CLOSE);
        }

        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex);

            ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }


//    @Override
//    public ListenableFuture<WrappedDataOriginStatus> processHttpRequest(HttpRequest httpRequest){
//        try {
//            URI proxyUri = new URI(httpRequest.getUri());
//            log.debug("Received request for proxy URI {}.", proxyUri);
//
//            //Look up the data origin associated with the URI contained in the HTTP request
//            DataOrigin<T> dataOrigin = getDataOrigin(proxyUri);
//            if(dataOrigin == null){
//                String message = String.format("Data origin for proxy URI %s not found.", proxyUri.toString());
//                throw new DataOriginAccessException(HttpResponseStatus.NOT_FOUND, message);
//            }
//            log.debug("Found data origin for proxy URI {} (identifier: \"{}\")", proxyUri, dataOrigin.getIdentifier());
//
//
//            //Look up appropriate accessor for data origin
//            DataOriginAccessor<T> dataOriginAccessor = getDataOriginAccessor(dataOrigin);
//            if(dataOriginAccessor == null){
//                String message = String.format("No data origin accessor found for data origin with identifier %s",
//                    dataOrigin.getIdentifier().toString());
//                throw new DataOriginAccessException(HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
//            }
//            log.debug("Accessor found for data origin with identifier \"{}\".", dataOrigin.getIdentifier());
//
//            //Retrieve status from data origin
//            return dataOriginAccessor.retrieveStatus(dataOrigin, true);
//        }
//        catch (Exception ex) {
//            SettableFuture<WrappedDataOriginStatus> statusFuture = SettableFuture.create();
//            statusFuture.setException(ex);
//            return statusFuture;
//        }
//    }

    /**
     * Returns an appropriate {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} to access the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} or <code>null</code> if no such
     * {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} exists.
     *
     * @param dataOrigin the {@link eu.spitfire.ssp.backends.generic.DataOrigin} to return the associates
     *                   {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} for
     *
     * @return an appropriate {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} to access the given
     * {@link eu.spitfire.ssp.backends.generic.DataOrigin} or <code>null</code> if no such
     * {@link eu.spitfire.ssp.backends.generic.access.DataOriginAccessor} exists.
     */
    public abstract DataOriginAccessor<T> getDataOriginAccessor(DataOrigin<T> dataOrigin);

    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.DataOrigin} associated with the given proxy URI or
     * <code>null</code> if no such {@link eu.spitfire.ssp.backends.generic.DataOrigin} exists.
     *
     * @param proxyUri the {@link java.net.URI} to return the associated
     *                 {@link eu.spitfire.ssp.backends.generic.DataOrigin} for
     *
     * @return the {@link eu.spitfire.ssp.backends.generic.DataOrigin} associated with the given proxy URI or
     * <code>null</code> if no such {@link eu.spitfire.ssp.backends.generic.DataOrigin} exists.
     */
    public abstract DataOrigin<T> getDataOrigin(URI proxyUri);


    public abstract boolean removeDataOriginOnAccessFailure(DataOrigin<T> dataOrigin);
}
