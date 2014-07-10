package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.server.internal.messages.requests.DataOriginRegistration;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.internal.messages.responses.DataOriginAccessError;
import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.Language;
import eu.spitfire.ssp.utils.exceptions.IdentifierAlreadyRegisteredException;
import eu.spitfire.ssp.utils.exceptions.WebserviceAlreadyRegisteredException;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * The generic proxy Webservice to translate incoming {@link org.jboss.netty.handler.codec.http.HttpRequest}s to
 * a proper format to perform the desired operation (GET, POST, PUT, DELETE) on a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
 *
 * Each backend has its own (automatically created) instance of
 * {@link DataOriginMapper}.
 *
 * @author Oliver Kleine
 */
public class DataOriginMapper<I, D extends DataOrigin<I>> extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final Object monitor = new Object();
    private Map<String, D> proxyUriToDataOrigin;
    private Map<I, D> identifierToDataOrigin;

    protected BackendComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link DataOriginMapper}.
     * @param componentFactory the {@link eu.spitfire.ssp.backends.generic.BackendComponentFactory} that amongst other
     *                         things provides the components to forward incoming HTTP requests to the appropriate
     *                         {@link eu.spitfire.ssp.backends.generic.DataOrigin}.
     */
    public DataOriginMapper(BackendComponentFactory<I, D> componentFactory){
        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), null);
        this.componentFactory = componentFactory;
        this.proxyUriToDataOrigin = new HashMap<>();
        this.identifierToDataOrigin = new HashMap<>();
    }


    /**
     * Returns the name of the backend this {@link DataOriginMapper} instance
     * is working for.
     *
     * @return the name of the backend this {@link DataOriginMapper} instance
     * is working for.
     */
    public String getBackendName(){
        return componentFactory.getBackendName();
    }


    /**
     * Returns the {@link eu.spitfire.ssp.backends.generic.DataOrigin} which is identified by the given identifier
     * @param identifier the identifier to find the {@link eu.spitfire.ssp.backends.generic.DataOrigin} for
     * @return the {@link eu.spitfire.ssp.backends.generic.DataOrigin} which is identified by the given identifier
     */
    public D getDataOrigin(I identifier){
        return identifierToDataOrigin.get(identifier);
    }


    /**
     * Handles instances of {@link eu.spitfire.ssp.server.internal.messages.requests.DataOriginRegistration}
     * @param ctx
     * @param me
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent me) throws Exception {

        if(me.getMessage() instanceof DataOriginRegistration){

            DataOriginRegistration<I, D> registration = (DataOriginRegistration<I, D>) me.getMessage();
            SettableFuture<?> registrationFuture = registration.getRegistrationFuture();

            final D dataOrigin = registration.getDataOrigin();
            final String proxyUri = "/?graph=" + dataOrigin.getGraphName();

            try{
                addDataOrigin(proxyUri, dataOrigin);

                Futures.addCallback(registrationFuture, new FutureCallback<Object>() {

                    @Override
                    public void onSuccess(@Nullable Object result) {
                        //nothing to do...
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        if(!(t instanceof IdentifierAlreadyRegisteredException) &&
                                !(t instanceof WebserviceAlreadyRegisteredException)){
                            removeDataOrigin(proxyUri, dataOrigin);
                        }
                    }
                });
            }

            catch (IdentifierAlreadyRegisteredException ex) {
                log.warn("Data origin {} was already registered!", ex.getIdentifier());
                registrationFuture.setException(ex);
            }

        }

        ctx.sendDownstream(me);
    }


    private void addDataOrigin(String proxyUri, D dataOrigin) throws IdentifierAlreadyRegisteredException{

        I identifier = dataOrigin.getIdentifier();

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


    private void removeDataOrigin(String proxyUri, D dataOrigin){

        I identifier = dataOrigin.getIdentifier();

        if(identifierToDataOrigin.containsKey(identifier)){
            synchronized (monitor){
                identifierToDataOrigin.remove(identifier);
                proxyUriToDataOrigin.remove(proxyUri);
            }
        }
    }


    @Override
    public void processHttpRequest(final Channel channel, final HttpRequest httpRequest,
                                   final InetSocketAddress clientAddress) throws Exception{

        try{
            String proxyUri = httpRequest.getUri();

            //Look up the data origin associated with the URI contained in the HTTP request
            final D dataOrigin = this.proxyUriToDataOrigin.get(proxyUri);

            if(dataOrigin == null){
                String content = String.format("Data origin for proxy URI %s not found.", proxyUri);
                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                        httpRequest.getProtocolVersion(), HttpResponseStatus.NOT_FOUND, content
                );

                writeHttpResponse(channel, httpResponse, clientAddress);
                return;
            }

            log.debug("Found data origin for proxy URI {} (identifier: \"{}\")", proxyUri, dataOrigin.getIdentifier());

            //Look up appropriate accessor for proxy URI
            Accessor<I, D> accessor = this.componentFactory.getAccessor(dataOrigin);

            if(accessor == null){
                String content = String.format("No data origin accessor found for data origin with identifier %s",
                        dataOrigin.getIdentifier().toString());

                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                        httpRequest.getProtocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR, content
                );

                writeHttpResponse(channel, httpResponse, clientAddress);
                return;
            }

            log.debug("Found data origin accessor for proxy URI {} (identifier: \"{}\")", proxyUri,
                    dataOrigin.getIdentifier());


            ListenableFuture<?> accessFuture;

            if(httpRequest.getMethod() == HttpMethod.GET){
                accessFuture = accessor.getStatus(dataOrigin);
            }

            else if(httpRequest.getMethod() == HttpMethod.PUT){
                ChannelBufferInputStream inputStream = new ChannelBufferInputStream(httpRequest.getContent());
                Model model = ModelFactory.createDefaultModel();
                model.read(inputStream, null, Language.RDF_N3.lang);
                accessFuture = accessor.setStatus(dataOrigin, model);
            }

            else if(httpRequest.getMethod() == HttpMethod.DELETE){
                accessFuture = accessor.deleteResource(dataOrigin);
            }

            else{
                SettableFuture<DataOriginAccessError> tmpFuture = SettableFuture.create();
                String message = "HTTP " + httpRequest.getMethod() + " is not supported. Try GET, PUT, or DELETE)";
                tmpFuture.set(new DataOriginAccessError(AccessResult.Code.NOT_ALLOWED, message));
                accessFuture = tmpFuture;
            }

            Futures.addCallback(accessFuture, new FutureCallback<Object>() {

                @Override
                public void onSuccess(Object accessResult) {
                    ChannelFuture future = Channels.write(channel, accessResult, clientAddress);
                    future.addListener(ChannelFutureListener.CLOSE);

                    if(log.isDebugEnabled()){
                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                log.debug("Succesfully written status of graph {} to !",
                                        dataOrigin.getGraphName(), clientAddress);
                            }
                        });
                    }
                }


                @Override
                public void onFailure(Throwable throwable) {
                    HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(
                            httpRequest.getProtocolVersion(),
                            HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            throwable.getMessage()
                    );

                    writeHttpResponse(channel, httpResponse, clientAddress);
                }

            }, this.getIoExecutor());

        }

        catch(Exception ex){
            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex);

            log.error("Exception while processing HTTP proxy request!", ex);
            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }
}
