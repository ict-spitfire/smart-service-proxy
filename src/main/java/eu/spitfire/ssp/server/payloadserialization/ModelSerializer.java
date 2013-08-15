package eu.spitfire.ssp.server.payloadserialization;

import com.hp.hpl.jena.rdf.model.Model;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.ByteArrayOutputStream;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 16.07.13
 * Time: 11:31
 * To change this template use File | Settings | File Templates.
 */
public class ModelSerializer {

    public static ChannelBuffer serializeModel(Model model){
        return serializeModel(model, Language.DEFAULT_MODEL_LANGUAGE);
    }

    public static ChannelBuffer serializeModel(Model model, Language language){
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();


//            //Rename CoAP URIs of subjects contained in the payload to HTTP URIs
//            ResIterator iterator = model.listSubjects();
//            while(iterator.hasNext()){
//                Resource subject = iterator.nextResource();
//                String uri = subject.getURI();
//                if(uri != null && uri.startsWith("coap://")){
//                    ResourceUtils.renameResource(subject, createHttpMirrorUri(uri));
//                }
//            }
//
//            //Rename CoAP URIs of objects contained in the payload to HTTP URIs
//            NodeIterator iterator2 = model.listObjects();
//            while(iterator2.hasNext()){
//                RDFNode object = iterator2.nextNode();
//                if(object != null  && object.isResource()){
//                    Resource objectResource = object.asResource();
//                    String uri = objectResource.getURI();
//                    if(uri != null && uri.startsWith("coap://")){
//                        ResourceUtils.renameResource(objectResource, createHttpMirrorUri(uri));
//                    }
//                }
//            }


        //Serialize model and write on OutputStream
        model.write(byteArrayOutputStream, language.lang);

        return ChannelBuffers.wrappedBuffer(byteArrayOutputStream.toByteArray());

    }


}
