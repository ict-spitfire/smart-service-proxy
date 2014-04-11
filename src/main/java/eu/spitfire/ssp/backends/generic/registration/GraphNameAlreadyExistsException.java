package eu.spitfire.ssp.backends.generic.registration;

/**
 * Created by olli on 10.04.14.
 */
public class GraphNameAlreadyExistsException extends Exception{

    private String graphName;


    public GraphNameAlreadyExistsException(String graphName) {
        super("Graph with name " + graphName + " does already exist!");
        this.graphName = graphName;
    }


    public String getGraphName() {
        return graphName;
    }
}
