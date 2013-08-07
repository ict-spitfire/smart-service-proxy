package eu.spitfire.ssp.gateway.coap.requestprocessing;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.uniluebeck.itm.ncoap.message.options.Option;
import de.uniluebeck.itm.ncoap.message.options.OptionList;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.OptionName;
import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType;
import eu.spitfire.ssp.core.payloadserialization.Language;
import org.apache.http.protocol.HttpDateGenerator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpVersion;


import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 16.07.13
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public abstract class CoapOptionHttpHeaderMapper {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final TimeZone HTTP_DATE_GMT_TIMEZONE = TimeZone.getDefault();

    public static SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
    static{
        dateFormatter.setTimeZone(HTTP_DATE_GMT_TIMEZONE);
    }

    public static Multimap<String, String> getHttpHeaders(OptionList coapOptions){

        Multimap<String, String> result = HashMultimap.create();

        //CONTENT-TYPE
        for(Option option : coapOptions.getOption(OptionName.CONTENT_TYPE)){
            MediaType mediaType = MediaType.getByNumber((Long) option.getDecodedValue());

            String mimeType = getHttpMimeType(mediaType);

            if(mimeType != null)
                result.put(HttpHeaders.Names.CONTENT_TYPE, mimeType);
        }

        //EXPIRES
        Long maxAge;
        if(coapOptions.getOption(OptionName.MAX_AGE).isEmpty())
            maxAge = new Long(OptionRegistry.MAX_AGE_DEFAULT);
        else
            maxAge = (Long) coapOptions.getOption(OptionName.MAX_AGE).get(0).getDecodedValue();

        Date expiryDate =  new Date(System.currentTimeMillis() + 1000 * maxAge);
        result.put(HttpHeaders.Names.EXPIRES, dateFormatter.format(expiryDate));

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
