package eu.spitfire.ssp.server.internal.utils;


import com.hp.hpl.jena.sparql.resultset.ResultsFormat;

/**
 * A wrapper class for the internal representation of serialization format names for SPARQL results in the JENA
 * framework and corresponding HTTP mime types.
 *
 * @author Oliver Kleine
 */
public enum QueryResultsFormat {

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
    private ResultsFormat resultsFormat;

    /**
     * The corresponding HTTP mime type
     */
    private String mimeType;


    private QueryResultsFormat(ResultsFormat resultsFormat, String mimeType){
        this.resultsFormat = resultsFormat;
        this.mimeType = mimeType;
    }

    /**
     * Returns the {@link QueryResultsFormat} according to the given HTTP mime type or
     * <code>null</code> if the given mime type is not supported.
     *
     * @param mimeType the HTTP mime type to lookup the corresponding {@link eu.spitfire.ssp.server.internal.utils.Language} for
     *
     * @return the {@link QueryResultsFormat} according to the given HTTP mime type or
     * <code>null</code> if the given mime type is not supported.
     */
    public static QueryResultsFormat getByHttpMimeType(String mimeType){
        for(QueryResultsFormat format : QueryResultsFormat.values()){
            if(mimeType.contains(format.mimeType))
                return format;
        }

        return null;
    }


    public static boolean isSupported(String mimeType){
        return getByHttpMimeType(mimeType) == null;
    }

    public ResultsFormat getResultsFormat() {
        return this.resultsFormat;
    }

    public String getMimeType() {
        return this.mimeType;
    }
}
