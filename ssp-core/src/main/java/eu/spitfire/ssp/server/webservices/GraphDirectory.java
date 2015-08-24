package eu.spitfire.ssp.server.webservices;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 09.07.14.
 */
public class GraphDirectory extends HttpWebservice{

    public GraphDirectory(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor) {
        super(ioExecutor, internalTasksExecutor, "html/services/graph-directory.html");
    }
}
