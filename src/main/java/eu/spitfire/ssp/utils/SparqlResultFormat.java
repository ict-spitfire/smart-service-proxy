package eu.spitfire.ssp.utils;

import com.hp.hpl.jena.sparql.resultset.ResultsFormat;

/**
 * Created by olli on 29.04.14.
 */
public enum SparqlResultFormat {

    XML(ResultsFormat.FMT_RS_XML, "application/sparql-results+xml"),
    JSON(ResultsFormat.FMT_RS_JSON, "application/sparql-results+json"),
    CSV(ResultsFormat.FMT_RS_CSV, "text/csv"),
    TSV(ResultsFormat.FMT_RS_TSV, "text/tab-separated-values");


    public ResultsFormat resultsFormat;
    public String mimeType;

    private SparqlResultFormat(ResultsFormat resultsFormat, String mimeType){
        this.resultsFormat = resultsFormat;
        this.mimeType = mimeType;
    }

    public static SparqlResultFormat getByHttpMimeType(String mimeType){
        for(SparqlResultFormat format : SparqlResultFormat.values()){
            if(mimeType.contains(format.mimeType))
                return format;
        }

        return null;
    }

}
