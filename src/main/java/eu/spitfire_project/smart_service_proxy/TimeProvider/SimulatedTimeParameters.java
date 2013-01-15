package eu.spitfire_project.smart_service_proxy.TimeProvider;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.text.SimpleDateFormat;

/**
 * User: Richard Mietz
 * Date: 09.10.12
 */
public class SimulatedTimeParameters
{
    //Switch to decide if using date or elapsed minutes
    public static boolean useDate = true;

    //To format a date as needed for xml schema datetime format
    public static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    //Start date
    //public static DateTime startDate = new DateTime(2012,10,24,6,0);
    public static DateTime startDate = new DateTime(2012,10,24,0,0);

    public static LocalTime skipTime = new LocalTime(20,15);
    public static int skipHours = 9;
    public static int skipMinutes = 45;

    //Time of the day (in total minutes) when to start the simulation
    public static int start = 0;

    //Update rate for the triple in seconds
    public static int updateRate = 2;

    //Simulation rounds
    public static int elapseRound = 0;

    //Elapsed time for one update in minutes
    public static int elapseMinutes = 15;

    //Number of minutes before starting from 0 again
    public static int overflowMod = 1440;

    //Address of sesame server
    public static String repoAdr = "http://localhost:80/openrdf-sesame";
    //Id of the repository
    public static String repositoryID = "sensors";

    //Base URI for semantic triples
    public static final String baseURI = "http://www.iti.uni-luebeck.de/";

    //Context
    public static final String context = baseURI + "timeContext";

    //Subject for time
    public static String subject = baseURI + "time";

    //Predicate for time
    public static String predicate = baseURI + "is";

    public static double actualTemperature = 0;
}
