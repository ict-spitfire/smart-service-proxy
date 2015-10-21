package eu.spitfire.ssp.backend.generic;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.internal.message.DataOriginReplacementRequest;
import eu.spitfire.ssp.server.internal.wrapper.ExpiringNamedGraph;
import eu.spitfire.ssp.server.internal.message.DataOriginRegistrationRequest;
import eu.spitfire.ssp.server.internal.exception.OperationNotSupportedException;
import eu.spitfire.ssp.server.webservices.HttpWebservice;
import eu.spitfire.ssp.server.internal.utils.HttpResponseFactory;
import eu.spitfire.ssp.server.internal.utils.Language;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The generic proxy Webservice to translate incoming {@link org.jboss.netty.handler.codec.http.HttpRequest}s to
 * a proper format to perform the desired operation (GET, POST, PUT, DELETE) on a
 * {@link eu.spitfire.ssp.backend.generic.DataOrigin}.
 *
 * Each backend has its own (automatically created) instance of
 * {@link DataOriginMapper}.
 *
 * @author Oliver Kleine
 */
public class DataOriginMapper<I, D extends DataOrigin<I>> extends HttpWebservice {

    private static Logger LOG = LoggerFactory.getLogger(DataOriginMapper.class.getName());

    private final Object monitor = new Object();
    private Map<String, D> proxyUriToDataOrigin;
    private Map<I, D> identifierToDataOrigin;

    protected ComponentFactory<I, D> componentFactory;

    /**
     * Creates a new instance of {@link DataOriginMapper}.
     * @param componentFactory the {@link ComponentFactory} that amongst other
     *                         things provides the components to forward incoming HTTP requests to the appropriate
     *                         {@link eu.spitfire.ssp.backend.generic.DataOrigin}.
     */
    public DataOriginMapper(ComponentFactory<I, D> componentFactory){
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
     * Returns the {@link eu.spitfire.ssp.backend.generic.DataOrigin} which is identified by the given identifier
     * @param identifier the identifier to find the {@link eu.spitfire.ssp.backend.generic.DataOrigin} for
     * @return the {@link eu.spitfire.ssp.backend.generic.DataOrigin} which is identified by the given identifier
     */
    public D getDataOrigin(I identifier){
        return identifierToDataOrigin.get(identifier);
    }


    /**
     * Handles instances of {@link eu.spitfire.ssp.server.internal.message.DataOriginRegistrationRequest}
     * @param ctx
     * @param me
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent me) throws Exception {

        if(me.getMessage() instanceof DataOriginRegistrationRequest){

            DataOriginRegistrationRequest<I, D> registration = (DataOriginRegistrationRequest<I, D>) me.getMessage();
            SettableFuture<?> registrationFuture = registration.getRegistrationFuture();

            final D dataOrigin = registration.getDataOrigin();
            final String proxyUri = "/?graph=" + dataOrigin.getGraphName();

            addDataOrigin(proxyUri, dataOrigin);

            Futures.addCallback(registrationFuture, new FutureCallback<Object>() {

                @Override
                public void onSuccess(Object result) {
                    //nothing to do...
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Error in registration of {}", proxyUri, throwable);
                    removeDataOrigin(proxyUri, dataOrigin);
                }
            });

        }

        else if(me.getMessage() instanceof DataOriginReplacementRequest){
            DataOriginReplacementRequest<I, D> request = (DataOriginReplacementRequest<I, D>) me.getMessage();
            replaceDataOrigin(request.getOldDataOrigin(), request.getNewDataOrigin());
        }

        ctx.sendDownstream(me);
    }


    private void addDataOrigin(String proxyUri, D dataOrigin){

        I identifier = dataOrigin.getIdentifier();

        try {
            synchronized (monitor) {
                DataOrigin old = identifierToDataOrigin.put(identifier, dataOrigin);
                proxyUriToDataOrigin.put(proxyUri, dataOrigin);

                if(old != null){
                    old.shutdown();
                }

                LOG.info("Added graph \"{}\" from data origin \"{}\" to backend \"{}\"",
                        new Object[]{dataOrigin.getGraphName(), identifier, this.getBackendName()});
            }
        }
        catch(Exception ex){
            LOG.error("This should never happen!", ex);
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

    private void replaceDataOrigin(D oldDataOrigin, D newDataOrigin){
        final String oldProxyUri = "/?graph=" + oldDataOrigin.getGraphName();
        removeDataOrigin("/?graph=" + oldDataOrigin.getGraphName(), oldDataOrigin);
        addDataOrigin("/?graph=" + newDataOrigin.getGraphName(), newDataOrigin);
    }

    @Override
    public void processHttpRequest(final Channel channel, final HttpRequest httpRequest,
                                   final InetSocketAddress clientAddress) throws Exception{

        try{
            String proxyUri = URLDecoder.decode(httpRequest.getUri().replace("+", "%2B"), "UTF-8").replace("%2B", "+");

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

            LOG.debug("Found data origin for proxy URI {} (identifier: \"{}\")", proxyUri, dataOrigin.getIdentifier());

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

            LOG.debug("Found data origin accessor for proxy URI {} (identifier: \"{}\")", proxyUri,
                    dataOrigin.getIdentifier());


            ListenableFuture<?> resultFuture;

            if(httpRequest.getMethod() == HttpMethod.GET){
                resultFuture = accessor.getStatus(dataOrigin);
            }

            else if(httpRequest.getMethod() == HttpMethod.PUT){
                ChannelBufferInputStream inputStream = new ChannelBufferInputStream(httpRequest.getContent());
                Model model = ModelFactory.createDefaultModel();
                model.read(inputStream, null, Language.RDF_TURTLE.getRdfFormat().getLang().getName());
                resultFuture = accessor.setStatus(dataOrigin, model);
            }

            else if(httpRequest.getMethod() == HttpMethod.DELETE){
                resultFuture = accessor.deleteResource(dataOrigin);
            }

            // only GET, PUT and DELETE are supported
            else{
                SettableFuture<?> tmp = SettableFuture.create();
                tmp.setException(new OperationNotSupportedException(
                    String.format(Locale.ENGLISH, "HTTP %s is not supported (try GET, PUT, and DELETE)",
                        httpRequest.getMethod())
                ));
                resultFuture = tmp;
            }

            Futures.addCallback(resultFuture, new FutureCallback<Object>() {

                @Override
                public void onSuccess(Object result) {
                    if(result instanceof Accessor.ModificationResult){
                        if(result.equals(Accessor.ModificationResult.DELETED)){
                           result = HttpResponseFactory.createHttpResponse(
                                httpRequest.getProtocolVersion(), HttpResponseStatus.NO_CONTENT, ""
                            );

                        }
                    }

                    ChannelFuture future = Channels.write(channel, result, clientAddress);
                    future.addListener(ChannelFutureListener.CLOSE);

                    if(LOG.isDebugEnabled()){
                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                LOG.debug("Succesfully written status of graph {} to !",
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

            LOG.error("Exception while processing HTTP proxy request!", ex);
            writeHttpResponse(channel, httpResponse, clientAddress);
        }
    }

    private void processGet(){
        SettableFuture<ExpiringNamedGraph> result = SettableFuture.create();


    }
}
