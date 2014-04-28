package eu.spitfire.ssp.backends.generic.access;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.server.exceptions.IdentifierAlreadyRegisteredException;
import eu.spitfire.ssp.server.messages.DataOriginRegistrationMessage;
import eu.spitfire.ssp.server.messages.GraphStatusMessage;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * The generic proxy Webservice to translate incoming {@link org.jboss.netty.handler.codec.http.HttpRequest}s to
 * a proper format to perform the desired operation (GET, POST, PUT, DELETE) on a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * @author Oliver Kleine
 */
public class HttpSemanticProxyWebservice<T> extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final Object monitor = new Object();
    private Map<String, DataOrigin<T>> proxyUriToDataOrigin;
    private Map<T, DataOrigin<T>> identifierToDataOrigin;

    protected BackendComponentFactory<T> componentFactory;


    public HttpSemanticProxyWebservice(BackendComponentFactory<T> componentFactory){
        this.componentFactory = componentFactory;
        this.proxyUriToDataOrigin = new HashMap<>();
        this.identifierToDataOrigin = new HashMap<>();
    }


    public String getBackendName(){
        return componentFactory.getBackendName();
    }


    @Override
    @SuppressWarnings("unchecked")
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me) throws Exception {

        if(me.getMessage() instanceof DataOriginRegistrationMessage){

            DataOriginRegistrationMessage<T> message = (DataOriginRegistrationMessage<T>) me.getMessage();

            final DataOrigin<T> dataOrigin = message.getDataOrigin();
            final String proxyUri = "/?graph=" + dataOrigin.getGraphName();

            try{
                addDataOrigin(proxyUri, dataOrigin);

                me.getFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if(!future.isSuccess())
                            removeDataOrigin(proxyUri, dataOrigin);
                    }
                });

                ctx.sendDownstream(me);
            }

            catch (IdentifierAlreadyRegisteredException e) {
                log.warn("Could not register new data origin!", e);
                me.getFuture().setFailure(e);
            }

        }

        super.writeRequested(ctx, me);
    }


    private void addDataOrigin(String proxyUri, DataOrigin<T> dataOrigin) throws IdentifierAlreadyRegisteredException{

        T identifier = dataOrigin.getIdentifier();

        if(!identifierToDataOrigin.containsKey(identifier)){
            synchronized (monitor){
                if(!identifierToDataOrigin.containsKey(identifier)){
                    identifierToDataOrigin.put(identifier, dataOrigin);
                    proxyUriToDataOrigin.put(proxyUri, dataOrigin);

                    log.info("Added graph \"{}\" from data origin \"{}\" to backend \"{}\"",
                            new Object[]{dataOrigin.getGraphName(), identifier, this.getBackendName()});
                }

                else{
                    throw new IdentifierAlreadyRegisteredException(identifier);
                }
            }
        }

        else{
            throw new IdentifierAlreadyRegisteredException(identifier);
        }
    }


    private void removeDataOrigin(String proxyUri, DataOrigin<T> dataOrigin){

        T identifier = dataOrigin.getIdentifier();

        if(identifierToDataOrigin.containsKey(identifier)){
            synchronized (monitor){
                identifierToDataOrigin.remove(identifier);
                proxyUriToDataOrigin.remove(proxyUri);
            }
        }
    }

    @Override
    public void processHttpRequest(final Channel channel, final HttpRequest httpRequest,
                                   final InetSocketAddress clientAddress){

        try{
            String proxyUri = httpRequest.getUri();

            //Look up the data origin associated with the URI contained in the HTTP request
            DataOrigin<T> dataOrigin = getDataOrigin(proxyUri);
            if(dataOrigin == null){
                String message = String.format("Data origin for proxy URI %s not found.", proxyUri);
                throw new DataOriginAccessException(HttpResponseStatus.NOT_FOUND, message);
            }
            log.debug("Found data origin for proxy URI {} (identifier: \"{}\")", proxyUri, dataOrigin.getIdentifier());


            //Look up appropriate accessor for proxy URI
            DataOriginAccessor<T> dataOriginAccessor = this.componentFactory.getDataOriginAccessor(dataOrigin);
            if(dataOriginAccessor == null){
                String message = String.format("No data origin accessor found for data origin with identifier %s",
                        dataOrigin.getIdentifier().toString());
                throw new DataOriginAccessException(HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
            }


            T identifier = dataOrigin.getIdentifier();

            HttpMethod httpMethod = httpRequest.getMethod();

            ListenableFuture resultFuture;

            if(httpMethod.equals(HttpMethod.GET)){
                resultFuture = handleGetRequest(channel, clientAddress, dataOriginAccessor, identifier);
            }

//            else if(httpMethod.equals(HttpMethod.PUT)){
//                httpResponseOnSuccess = true;
//                resultFuture = handlePutRequest(channel, clientAddress, dataOriginAccessor, identifier);
//            }
//
//            else if(httpMethod.equals(HttpMethod.DELETE)){
//                httpResponseOnSuccess = true;
//                resultFuture = handleDeleteRequest(channel, clientAddress, dataOriginAccessor, identifier);
//            }

            else
                throw new DataOriginAccessException(HttpResponseStatus.METHOD_NOT_ALLOWED,
                        "Method " + httpMethod + " is not allowed!");


            Futures.addCallback(resultFuture, new FutureCallback() {

                @Override
                public void onSuccess(Object object) {
                    //Nothing to do...
                }

                @Override
                public void onFailure(Throwable throwable) {
                    HttpVersion httpVersion = httpRequest.getProtocolVersion();
                    HttpResponse httpResponse;

                    if(throwable instanceof DataOriginAccessException){
                        DataOriginAccessException ex = (DataOriginAccessException) throwable;
                        httpResponse = HttpResponseFactory.createHttpResponse(httpVersion, ex.getHttpResponseStatus(),
                                ex);
                    }

                    else{
                        httpResponse = HttpResponseFactory.createHttpResponse(httpVersion,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());
                    }

                    writeHttpResponse(channel, httpResponse, clientAddress);
                }
            });

        }

        catch(DataOriginAccessException ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                   ex.getHttpResponseStatus(), ex);

            writeHttpResponse(channel, httpResponse, clientAddress);
        }

        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex);

            log.error("Exception while processing HTTP proxy request!", ex);
            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }


    private ListenableFuture<Void> handleGetRequest(final Channel channel, final InetSocketAddress clientAddress,
            DataOriginAccessor<T> dataOriginAccessor, T identifier) throws DataOriginAccessException {

        final SettableFuture<Void> resultFuture = SettableFuture.create();

        Futures.addCallback(dataOriginAccessor.getStatus(identifier), new FutureCallback<GraphStatusMessage>() {

            @Override
            public void onSuccess(GraphStatusMessage graphStatusMessage) {
                writeDataOriginStatusMessage(channel, graphStatusMessage, clientAddress);
                resultFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                resultFuture.setException(throwable);
            }

        }, componentFactory.getIoExecutorService());

        return resultFuture;
    }


//    private ListenableFuture<Void> handlePutRequest(Channel channel, InetSocketAddress clientAddress,
//            DataOriginAccessor<T> dataOriginAccessor, T identifier) throws DataOriginAccessException {
//
//        final SettableFuture<Void> resultFuture = SettableFuture.create();
//
//        Futures.addCallback(dataOriginAccessor.setStatus(identifier), new FutureCallback<ExpiringNamedGraph>() {
//
//            @Override
//            public void onSuccess(ExpiringNamedGraph namedGraphStatus) {
//                ExpiringNamedGraphStatusMessage namedGraphStatusMessage =
//                        new ExpiringNamedGraphStatusMessage(ExpiringGraphStatusMessage.StatusCode.OK, namedGraphStatus);
//
//                writeDataOriginStatusMessage(channel, namedGraphStatusMessage, clientAddress);
//
//                resultFuture.set(null);
//            }
//
//            @Override
//            public void onFailure(Throwable throwable) {
//                resultFuture.setException(throwable);
//            }
//
//        }, componentFactory.getIoExecutorService());
//
//        return resultFuture;
//    }


//    private ListenableFuture<Void> handleDeleteRequest(Channel channel, InetSocketAddress clientAddress,
//            DataOriginAccessor<T> dataOriginAccessor, T identifier) throws DataOriginAccessException {
//
//    }




    private void writeDataOriginStatusMessage(Channel channel, GraphStatusMessage statusMessage,
                                                InetSocketAddress clientAddress){

        ChannelFuture future = Channels.write(channel, statusMessage, clientAddress);
        future.addListener(ChannelFutureListener.CLOSE);

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.info("Succesfully written!");
            }
        });
    }



    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.DataOrigin} associated with the given proxy URI or
     * <code>null</code> if no such {@link eu.spitfire.ssp.backends.generic.DataOrigin} exists.
     *
     * @param proxyUri the local proxy URI (e.g. /?graph=http://www.example.org) to return the associated
     *                 {@link eu.spitfire.ssp.backends.generic.DataOrigin} for
     *
     * @return the {@link eu.spitfire.ssp.backends.generic.DataOrigin} associated with the given proxy URI or
     * <code>null</code> if no such {@link eu.spitfire.ssp.backends.generic.DataOrigin} exists.
     */
    public DataOrigin<T> getDataOrigin(String proxyUri){
        return proxyUriToDataOrigin.get(proxyUri);
    }
}
