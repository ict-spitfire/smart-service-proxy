package eu.spitfire.ssp.utils;

import com.hp.hpl.jena.sparql.resultset.ResultsFormat;

/**
 * A wrapper class for the internal representation of serialization format names for SPARQL results in the JENA
 * framework and corresponding HTTP mime types.
 *
 * @author Oliver Kleine
 */
public enum SparqlResultFormat {

    /**
     * Corresponds to HTTP mime type "application/sparql-results+xml"
     */
    XML(ResultsFormat.FMT_RS_XML, "application/sparql-results+xml"),

    /**
     * Corresponds to HTTP mime type "application/sparql-results+json"
     */
    JSON(ResultsFormat.FMT_RS_JSON, "application/sparql-results+json"),

    /**
     * Corresponds to HTTP mime type "text/csv"
     */
    CSV(ResultsFormat.FMT_RS_CSV, "text/csv"),

    /**
     * Corresponds to HTTP mime type "text/tab-separated-values"
     */
    TSV(ResultsFormat.FMT_RS_TSV, "text/tab-separated-values");

    /**
     * The corresponding {@link com.hp.hpl.jena.sparql.resultset.ResultsFormat}
     */
    public ResultsFormat resultsFormat;

    /**
     * The corresponding HTTP mime type
     */
    public String mimeType;


    private SparqlResultFormat(ResultsFormat resultsFormat, String mimeType){
        this.resultsFormat = resultsFormat;
        this.mimeType = mimeType;
    }


    /**
     * Returns the {@link eu.spitfire.ssp.utils.SparqlResultFormat} according to the given HTTP mime type or
     * <code>null</code> if the given mime type is not supported.
     *
     * @param mimeType the HTTP mime type to lookup the corresponding {@link eu.spitfire.ssp.utils.Language} for
     *
     * @return the {@link eu.spitfire.ssp.utils.SparqlResultFormat} according to the given HTTP mime type or
     * <code>null</code> if the given mime type is not supported.
     */
    public static SparqlResultFormat getByHttpMimeType(String mimeType){
        for(SparqlResultFormat format : SparqlResultFormat.values()){
            if(mimeType.contains(format.mimeType))
                return format;
        }

        return null;
    }

}
