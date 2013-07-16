package eu.spitfire.ssp.gateway.coap.requestprocessing;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.uniluebeck.itm.ncoap.message.options.Option;
import de.uniluebeck.itm.ncoap.message.options.OptionList;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.OptionName;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType;
import eu.spitfire.ssp.core.payloadserialization.Language;
import org.jboss.netty.handler.codec.http.HttpHeaders;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 16.07.13
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public abstract class CoapOptionHttpHeaderMapper {

    public static Multimap<String, String> getHttpHeaders(OptionList coapOptions){

        Multimap<String, String> result = HashMultimap.create();

        for(Option option : coapOptions.getOption(OptionName.CONTENT_TYPE)){
            MediaType mediaType = MediaType.getByNumber((Long) option.getDecodedValue());

            String mimeType = getHttpMimeType(mediaType);

            if(mimeType != null)
                result.put(HttpHeaders.Names.CONTENT_TYPE, mimeType);
        }

        return result;
    }

    private static String getHttpMimeType(MediaType mediaType){
        Language lang = Language.getByCoapMediaType(mediaType);
        if(lang != null)
            return lang.mimeType;
        else
            return null;
    }
}
