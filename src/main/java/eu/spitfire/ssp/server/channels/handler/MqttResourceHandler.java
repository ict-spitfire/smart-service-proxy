package eu.spitfire.ssp.server.channels.handler;

import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import de.uniluebeck.itm.spitfire.ssphttpobserveovermqttlib.HttpObserveOverMqttLib;
import eu.spitfire.ssp.backends.generic.messages.InternalRemoveResourcesMessage;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import eu.spitfire.ssp.utils.Language;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 01.10.13
* Time: 21:17
* To change this template use File | Settings | File Templates.
*/
public class MqttResourceHandler extends SimpleChannelDownstreamHandler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private HttpObserveOverMqttLib httpObserver;

    public MqttResourceHandler(String mqttBrokerUri, int mqttBrokerHttpPort) throws IOException, MqttException {
        httpObserver = new HttpObserveOverMqttLib();
        httpObserver.startServer(mqttBrokerHttpPort, mqttBrokerUri);
        log.info("MQTT Broker started");
    }


    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
        log.info("DO something********************************************************************");
        if(me.getMessage() instanceof InternalResourceStatusMessage){
            InternalResourceStatusMessage message = (InternalResourceStatusMessage) me.getMessage();
            final URI resourceUri = message.getResourceUri();
            final Model model = message.getModel();
            final Date expiry = message.getExpiry() != null ? message.getExpiry() : new Date(System.currentTimeMillis() + 10000);

            log.info("Add/Update resource {}", resourceUri);

            httpObserver.addObservableURL(resourceUri.toString());

            httpObserver.updateResource(resourceUri.toString(),
                new HttpObserveOverMqttLib.ResourcePayloadCallback() {
                    @Override
                    public HttpObserveOverMqttLib.HttpResponse getResponseForAccept(List<String> accept) {
                        log.info("Start MQTT update for resource {}", resourceUri);
                        Joiner joiner = Joiner.on(",").skipNulls();
                        String acceptHeader = joiner.join(accept);

                        Language acceptedLanguage = null;

                        Multimap<Double, String> acceptedMediaTypes =
                                SemanticPayloadFormatter.getAcceptedMediaTypes(acceptHeader);

                        acceptLookup:
                        for(Double priority : acceptedMediaTypes.keySet()){
                            for(String mimeType : acceptedMediaTypes.get(priority)){
                                acceptedLanguage = Language.getByHttpMimeType(mimeType);
                                if(acceptedLanguage != null){
                                    break acceptLookup;
                                }
                            }
                        }

                        if(acceptedLanguage == null){
                            return new HttpObserveOverMqttLib.HttpResponse("Content-Type not supported");
                        }


                        //Model model = resource.getModel();
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        model.write(byteArrayOutputStream, acceptedLanguage.lang);

                        String payload = new String(byteArrayOutputStream.toByteArray(), Charset.forName("UTF-8"));
                        log.info("End MQTT update for resource {}. New status: {}", resourceUri, payload);

                        HttpObserveOverMqttLib.HttpResponse response = new HttpObserveOverMqttLib.HttpResponse(payload);

                        response.addHeader("max-age", "" + (expiry.getTime() - System.currentTimeMillis())/1000);
                        response.addHeader("Content-Type", acceptedLanguage.mimeType);

                        response.setGraphValue(1000.0);
                        return response;
                    }
                });
        }

        if(me.getMessage() instanceof InternalRemoveResourcesMessage){
            InternalRemoveResourcesMessage message = (InternalRemoveResourcesMessage) me.getMessage();
            URI resourceUri = message.getResourceUri();
            httpObserver.removeObservableURL(resourceUri.toString());
        }

        ctx.sendDownstream(me);
    }
}
