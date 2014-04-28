package eu.spitfire.ssp.server.messages;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Created by olli on 28.04.14.
 */
public class EmptyGraphStatusMessage extends GraphStatusMessage {

    public EmptyGraphStatusMessage(HttpResponseStatus statusCode) {
        super(statusCode);
    }

    @Override
    public String toString(){
        return "[Empty Graph-Status-Message (HTTP Code: " + getStatusCode() + "]";
    }
}
