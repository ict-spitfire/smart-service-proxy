package eu.spitfire_project.smart_service_proxy.TimeProvider;

import java.lang.Override;import java.lang.Runnable;import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: Richard Mietz
 * Date: 09.10.12
 */
public class SimulatedTimeScheduler implements Runnable
{
    private static ScheduledExecutorService scheduler =  Executors.newSingleThreadScheduledExecutor();

    @Override
    public void run()
    {
       SimulatedTimeUpdater stu = new SimulatedTimeUpdater();
       //scheduler.scheduleAtFixedRate(stu,0,SimulatedTimeParameters.updateRate, TimeUnit.SECONDS);
    }
}
