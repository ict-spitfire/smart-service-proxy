package eu.spitfire.ssp.server.messages;

/**
 * Created by olli on 25.04.14.
 */
public class GraphStatusMessage {

   public static enum StatusCode {
        OK, CHANGED, DELETED
    }

    private StatusCode statusCode;

    public GraphStatusMessage(StatusCode statusCode){
        this.statusCode = statusCode;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

}
