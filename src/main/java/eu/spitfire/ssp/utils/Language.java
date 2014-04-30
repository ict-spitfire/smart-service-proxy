package eu.spitfire.ssp.utils;

import de.uniluebeck.itm.ncoap.message.options.ContentFormat;

/**
 * A wrapper class for the internal representation of content format names in the JENA framework and corresponding
 * HTTP mime types.
 *
 * @author Oliver Kleine
 */
public enum Language{
    /**
     * Corresponds to HTTP mime type "application/rdf+xml"
     */
    RDF_XML("RDF/XML", "application/rdf+xml"),

    /**
     * Corresponds to HTTP mime type "application/n3"
     */
    RDF_N3 ("N3", "application/n3"),

    /**
     * Corresponds to HTTP mime type "application/turtle"
     */
    RDF_TURTLE("TURTLE", "application/turtle");

    /**
     * A String representing the JENA name of the language
     */
    public String lang;

    /**
     * A String representing the HTTP mime type of the language
     */
    public String mimeType;


    private Language(String lang, String mimeType) {
        this.lang = lang;
        this.mimeType = mimeType;
    }

    /**
     * Returns the {@link eu.spitfire.ssp.utils.Language} according to the given HTTP mime type or <code>null</code>
     * if the given mime type is not supported.
     *
     * @param mimeType the HTTP mime type to lookup the corresponding {@link eu.spitfire.ssp.utils.Language} for
     *
     * @return the {@link eu.spitfire.ssp.utils.Language} according to the given HTTP mime type or <code>null</code>
     * if the given mime type is not supported.
     */
    public static Language getByHttpMimeType(String mimeType){
        for(Language language : Language.values()){
            if(mimeType.contains(language.mimeType))
                return language;
        }

        return null;
    }

    public static Language getByCoapContentFormat(long contentFormat){

        if(contentFormat == ContentFormat.APP_RDF_XML)
            return RDF_XML;
        if(contentFormat == ContentFormat.APP_N3)
            return RDF_N3;
        if(contentFormat == ContentFormat.APP_TURTLE)
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
