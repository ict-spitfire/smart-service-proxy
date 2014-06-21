package eu.spitfire.ssp.server.common.messages;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.resultset.ResultSetMem;

/**
* Created by olli on 29.04.14.
*/
public class SparqlQueryResultMessage {

    private ResultSetMem queryResult;

    public SparqlQueryResultMessage(ResultSet resultSet) {
        queryResult = new ResultSetMem(resultSet);
    }


    /**
     * Returns a {@link com.hp.hpl.jena.query.QuerySolution} if there is a next one or <code>null</code> if there
     * are no more entries.
     *
     * @return a {@link com.hp.hpl.jena.query.QuerySolution} if there is a next one or <code>null</code> if there
     * are no more entries.
     */
    public QuerySolution getNextSolution() {
        if(queryResult.hasNext())
            return queryResult.next();
        else
            return null;
    }


    public ResultSet getQueryResult(){
        return this.queryResult;
    }
}
