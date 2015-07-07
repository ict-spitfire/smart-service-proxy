//package eu.spitfire.ssp.server.internal.messages.responses;
//
//import com.hp.hpl.jena.query.QuerySolution;
//import com.hp.hpl.jena.query.ResultSet;
//import com.hp.hpl.jena.sparql.resultset.ResultSetMem;
//
///**
// * Wrapper class that contains the result of a SPARQL query that was executed on the
// * {@link eu.spitfire.ssp.server.handler.SemanticCache}.
// *
// * @author Oliver Kleine
// */
//public class QueryResult {
//
//    private ResultSetMem resultSet;
//
//    public QueryResult(ResultSet resultSet) {
//        this.resultSet = new ResultSetMem(resultSet);
//    }
//
//    /**
//     * Returns a {@link com.hp.hpl.jena.query.QuerySolution} if there is a next one or <code>null</code> if there
//     * are no more entries.
//     *
//     * @return a {@link com.hp.hpl.jena.query.QuerySolution} if there is a next one or <code>null</code> if there
//     * are no more entries.
//     */
//    public QuerySolution getNextSolution() {
//        if(resultSet.hasNext())
//            return resultSet.next();
//        else
//            return null;
//    }
//
//
//    /**
//     * Returns the {@link com.hp.hpl.jena.query.ResultSet} that contains the results, i.e. solutions of the SPARQL
//     * query execution.
//     *
//     * @return the {@link com.hp.hpl.jena.query.ResultSet} that contains the results, i.e. solutions of the SPARQL
//     * query execution.
//     */
//    public ResultSet getResultSet(){
//        return this.resultSet;
//    }
//
//
//    @Override
//    public String toString() {
//        return "[Query Execution Success (Solutions: " + resultSet.size() + ")]";
//    }
//}
