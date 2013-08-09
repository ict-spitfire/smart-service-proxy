package eu.spitfire.ssp.core.payloadserialization;

import de.uniluebeck.itm.ncoap.message.options.OptionRegistry.MediaType;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 16.07.13
 * Time: 13:40
 * To change this template use File | Settings | File Templates.
 */
public enum Language{

    RDF_XML("RDF/XML", "application/rdf+xml"),
    RDF_N3 ("N3", "application/n3"),
    RDF_TURTLE("TURTLE", "application/turtle");

    public static Language DEFAULT_MODEL_LANGUAGE = Language.RDF_XML;

    public String lang;
    public String mimeType;

    private Language(String lang, String mimeType) {
        this.lang = lang;
        this.mimeType = mimeType;
    }

    public static Language getByHttpMimeType(String mimeType){
        for(Language language : Language.values()){
            if(mimeType.contains(language.mimeType))
                return language;
        }

        return null;
    }

    public static Language getByCoapMediaType(MediaType mediaType){

        if(mediaType == MediaType.APP_RDF_XML)
            return RDF_XML;
        if(mediaType == MediaType.APP_N3)
            return RDF_N3;
        if(mediaType == MediaType.APP_TURTLE)
            return RDF_TURTLE;

        return null;
    }

    public static Language getByName(String name){
        for(Language language : Language.values()){
            if(name.equals(language.lang))
                return language;
        }

        return null;
    }
}
