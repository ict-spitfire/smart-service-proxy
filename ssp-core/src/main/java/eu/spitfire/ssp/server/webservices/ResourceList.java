package eu.spitfire.ssp.server.webservices;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by olli on 19.08.15.
 */
public class ResourceList extends HttpWebservice{

    public ResourceList(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExecutor) {
        super(ioExecutor, internalTasksExecutor, "html/semantic-entities/resource-list.html");
    }
}
