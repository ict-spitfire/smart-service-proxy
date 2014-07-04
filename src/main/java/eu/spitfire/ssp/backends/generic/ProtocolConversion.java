package eu.spitfire.ssp.backends.generic;

import com.google.common.util.concurrent.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.Accessor;
import eu.spitfire.ssp.server.common.messages.DataOriginRegistrationMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusErrorMessage;
import eu.spitfire.ssp.server.common.messages.GraphStatusMessage;
import eu.spitfire.ssp.server.http.HttpResponseFactory;
import eu.spitfire.ssp.server.http.webservices.HttpWebservice;
import eu.spitfire.ssp.utils.Language;
import eu.spitfire.ssp.utils.exceptions.IdentifierAlreadyRegisteredException;
import org.jboss.netty.buffer.ChannelBufferInputStream;
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
public class ProtocolConversion<T> extends HttpWebservice {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final Object monitor = new Object();
    private Map<String, DataOrigin<T>> proxyUriToDataOrigin;
    private Map<T, DataOrigin<T>> identifierToDataOrigin;

    protected BackendComponentFactory<T> componentFactory;


    public ProtocolConversion(BackendComponentFactory<T> componentFactory){
        super(componentFactory.getIoExecutor(), componentFactory.getInternalTasksExecutor(), null);
        this.componentFactory = componentFactory;
        this.proxyUriToDataOrigin = new HashMap<>();
        this.identifierToDataOrigin = new HashMap<>();
    }


    public String getBackendName(){
        return componentFactory.getBackendName();
    }


    public DataOrigin<T> getDataOrigin(T identifier){
        return identifierToDataOrigin.get(identifier);
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
                log.warn("Data origin {} was already registered!", e.getIdentifier());
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
                                   final InetSocketAddress clientAddress) throws Exception{

        try{
            String proxyUri = httpRequest.getUri();

            //Look up the data origin associated with the URI contained in the HTTP request
            final DataOrigin<T> dataOrigin = this.proxyUriToDataOrigin.get(proxyUri);

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
            Accessor<T> accessor = this.componentFactory.getAccessor(dataOrigin);

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


            ListenableFuture<GraphStatusMessage> graphStatusFuture;

            if(httpRequest.getMethod() == HttpMethod.GET){
                graphStatusFuture = accessor.getStatus(dataOrigin);
            }

            else if(httpRequest.getMethod() == HttpMethod.PUT){
                ChannelBufferInputStream inputStream = new ChannelBufferInputStream(httpRequest.getContent());
                Model model = ModelFactory.createDefaultModel();
                model.read(inputStream, null, Language.RDF_N3.lang);
                graphStatusFuture = accessor.setStatus(dataOrigin, model);
            }

            else if(httpRequest.getMethod() == HttpMethod.DELETE){
                graphStatusFuture = accessor.deleteResource(dataOrigin);
            }

            else{
                graphStatusFuture = SettableFuture.create();
                ((SettableFuture<GraphStatusMessage>) graphStatusFuture).set(
                        new GraphStatusErrorMessage(
                                HttpResponseStatus.METHOD_NOT_ALLOWED,
                                "HTTP " + httpRequest.getMethod() + " is not supported. Try GET, PUT, or DELETE)"
                        )
                );
            }

            Futures.addCallback(graphStatusFuture, new FutureCallback<GraphStatusMessage>() {

                @Override
                public void onSuccess(GraphStatusMessage graphStatus) {
                    ChannelFuture future = Channels.write(channel, graphStatus, clientAddress);
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
