package eu.spitfire.ssp.server.internal.wrapper;

import org.apache.jena.query.ResultSet;

/**
 * Created by olli on 20.08.15.
 */
public class QueryExecutionResults {

    private long duration;
    private ResultSet resultSet;

    public QueryExecutionResults(long duration, ResultSet resultSet){
        this.duration = duration;
        this.resultSet = resultSet;
    }

    public long getDuration() {
        return duration;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }
}
