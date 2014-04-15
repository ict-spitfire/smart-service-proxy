package eu.spitfire.ssp.backends.generic.access;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.registration.IdentifierAlreadyRegisteredException;
import eu.spitfire.ssp.backends.generic.registration.InternalRegisterDataOriginMessage;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.HttpResponseFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

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

        if(me.getMessage() instanceof InternalRegisterDataOriginMessage){

            InternalRegisterDataOriginMessage<T> message = (InternalRegisterDataOriginMessage<T>) me.getMessage();

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
                log.error("Could not register new data origin!", e);
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


            //Forward message to data origin accessor
//            boolean removeOnFailure = removeDataOriginOnAccessFailure(dataOrigin);
            T identifier = dataOrigin.getIdentifier();
            ListenableFuture<WrappedDataOriginStatus> resultFuture = dataOriginAccessor.getStatus(identifier);

            Futures.addCallback(resultFuture, new FutureCallback<WrappedDataOriginStatus>() {

                @Override
                public void onSuccess(WrappedDataOriginStatus dataOriginStatus) {

                    if((dataOriginStatus.getCode() == WrappedDataOriginStatus.Code.DELETED) ||
                            ((dataOriginStatus.getCode() == WrappedDataOriginStatus.Code.OK ||
                                dataOriginStatus.getCode() == WrappedDataOriginStatus.Code.CHANGED)
                                    &&
                            (dataOriginStatus.getStatus() == null || dataOriginStatus.getStatus().isEmpty()))){

                        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                                HttpResponseStatus.NO_CONTENT);
                        writeHttpResponse(channel, httpResponse, clientAddress);
                    }

                    else{
                        DataOriginStatusMessage dataOriginStatusMessage = new DataOriginStatusMessage(dataOriginStatus);
                        writeDataOriginStatusMessage(channel, dataOriginStatusMessage, clientAddress);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());

                    writeHttpResponse(channel, httpResponse, clientAddress);
                }

            }, componentFactory.getIoExecutorService());
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


    protected void writeDataOriginStatusMessage(Channel channel, DataOriginStatusMessage statusMessage,
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
