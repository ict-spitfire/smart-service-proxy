package eu.spitfire.ssp.server.common.messages;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Created by olli on 28.04.14.
 */
public class GraphStatusErrorMessage extends GraphStatusMessage {

    private String errorMessage;

    public GraphStatusErrorMessage(HttpResponseStatus statusCode, String errorMessage) {
        super(statusCode);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString(){
        return "[Graph Status Error (HTTP Code: " + getStatusCode() + ", Message: " + getErrorMessage() + "]";
    }
}
