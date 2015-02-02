package eu.spitfire.ssp.backends.internal.se.webservices;

import eu.spitfire.ssp.server.webservices.HttpWebservice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 09.07.14.
 */
public class SemanticEntitiesEditor extends HttpWebservice{

    public SemanticEntitiesEditor(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor) {
        super(ioExecutor, internalTasksExecutor, "html/semantic-entities/semantic-entities-editor.html");
    }
}
