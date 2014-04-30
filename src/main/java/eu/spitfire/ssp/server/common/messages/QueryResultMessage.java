package eu.spitfire.ssp.server.common.messages;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.resultset.ResultSetMem;

/**
* Created by olli on 29.04.14.
*/
public class QueryResultMessage {

    private ResultSetMem queryResult;
//    private LinkedList<QuerySolution> queryResult;


    public QueryResultMessage(ResultSet resultSet) {
//        this.queryResult = new LinkedList<>();
        queryResult = new ResultSetMem(resultSet);
//        while(resultSet.hasNext())
//            queryResult.add(resultSet.next());
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
