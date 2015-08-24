package eu.spitfire.ssp.server.webservices;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 19.08.15.
 */
public class ResourceDirectory extends HttpWebservice{

    public ResourceDirectory(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor) {
        super(ioExecutor, internalTasksExecutor, "html/services/resource-directory.html");
    }
}
