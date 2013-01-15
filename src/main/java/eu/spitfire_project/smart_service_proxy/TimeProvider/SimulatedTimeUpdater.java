package eu.spitfire_project.smart_service_proxy.TimeProvider;

import org.joda.time.DateTime;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * User: Richard Mietz
 * Date: 09.10.12
 */
//public class SimulatedTimeUpdater implements Runnable
public class SimulatedTimeUpdater
{
    private ValueFactory vf = null;
    private Repository repo = null;
    private RepositoryConnection conn = null;
    private boolean timeInserted = false;

    private Resource s;
    private URI p;
    private Resource c;

    public SimulatedTimeUpdater()
    {
        try
        {
            repo = new HTTPRepository(SimulatedTimeParameters.repoAdr,
                    SimulatedTimeParameters.repositoryID);
            repo.initialize();
            repo.getConnection().setAutoCommit(false);
            vf = repo.getValueFactory();

            s = createSubject(SimulatedTimeParameters.subject);
            p = createPredicate(SimulatedTimeParameters.predicate);
            c = createContext(SimulatedTimeParameters.context);

        } catch (Exception e)
        {
            e.printStackTrace();
        }


    }

    //@Override
    //public void run()
    public void doit(long minSinceStart)
    {
        long begin = System.currentTimeMillis();
        System.out.println("Start doit.");

        String time;
        /*
        if (SimulatedTimeParameters.useDate)
        {
            DateTime simulatedTime = SimulatedTimeParameters.startDate.plusMinutes(SimulatedTimeParameters.elapseMinutes * SimulatedTimeParameters.elapseRound);
            if(simulatedTime.toLocalTime().compareTo(SimulatedTimeParameters.skipTime)>=0)
            {
                simulatedTime = simulatedTime.plusHours(SimulatedTimeParameters.skipHours);
                //simulatedTime = simulatedTime.plusMinutes(SimulatedTimeParameters.skipMinutes-15);
                simulatedTime = simulatedTime.plusMinutes(SimulatedTimeParameters.skipMinutes);
                SimulatedTimeParameters.elapseRound +=
                    1 + ((SimulatedTimeParameters.skipHours * 60 + SimulatedTimeParameters.skipMinutes)
                    / SimulatedTimeParameters.elapseMinutes);

            } else {
                SimulatedTimeParameters.elapseRound++;
            }
            time = SimulatedTimeParameters.dateFormatter.format(simulatedTime.toDate());
        } else
        {
            int simulatedTime = (SimulatedTimeParameters.start + SimulatedTimeParameters.elapseRound * SimulatedTimeParameters.elapseMinutes) % SimulatedTimeParameters.overflowMod;
            time = Integer.toString(simulatedTime);
        }*/
        DateTime simulatedTime = SimulatedTimeParameters.startDate.plusMinutes((int)minSinceStart);
        time = SimulatedTimeParameters.dateFormatter.format(simulatedTime.toDate());
        try
        {
            conn = repo.getConnection();
            conn.setAutoCommit(false);
            if (! timeInserted /* SimulatedTimeParameters.elapseRound <= 1*/)
            {
                timeInserted = true;
                insertStatement(time);
            } else
            {
                updateStatement(time);
            }
            conn.commit();
            retrieveTemperature();
            conn.commit();
            conn.close();
            long end = System.currentTimeMillis();
            System.out.println("End doit " + (end-begin) + " millis");
        } catch (RepositoryException e)
        {
            e.printStackTrace();
        }
        //System.out.println(time);
    }

    private void retrieveTemperature()
    {
        System.out.println("Start retreiveTemperature");
        String query = "SELECT ?temp WHERE { " +
                "<" + SimulatedTimeParameters.subject + "> <" + SimulatedTimeParameters.predicate + "> ?time ." +
                "?forecast	<http://spitfire-project.eu/ontology/ns/value> ?temp . " +
                "?forecast <http://spitfire-project.eu/ontology/ns/sn/time_start> ?start . " +
                "?forecast <http://spitfire-project.eu/ontology/ns/sn/time_end> ?end . " +
                "FILTER (?start <= ?time && ?end >= ?time)" +
                "}";


        try
        {
            System.out.println("Before prepareTupleQuery.");
            TupleQuery tupleQuery = conn.prepareTupleQuery(
                    QueryLanguage.SPARQL, query);
            System.out.println("Before evaluate");
            TupleQueryResult tqr = tupleQuery.evaluate();
            if (tqr.hasNext())
            {
                BindingSet bs = tqr.next();
                SimulatedTimeParameters.actualTemperature = Double.parseDouble(bs.getValue("temp").stringValue());
                //System.out.println("Current temp: " + String.valueOf(SimulatedTimeParameters.actualTemperature));
            }
            System.out.println("End retreiveTemperature");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void insertStatement(String timeObj)
    {
        Value o = createLiteral(timeObj);

        try
        {
            conn.add(s, p, o, c);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void updateStatement(String timeObj)
    {
        deleteTriple();
        insertStatement(timeObj);
    }

    private boolean deleteTriple()
    {
        try
        {

            conn.remove(s, p, null, (Resource) c);
        } catch (RepositoryException e)
        {
            System.out.println("Could not delete triple.");
            return false;
        }
        return false;
    }

    private Resource createSubject(String sub)
    {
        Resource s = null;
        try
        {
            s = vf.createURI(sub);

        } catch (IllegalArgumentException e)
        {
            s = vf.createBNode(sub);
        }
        return s;
    }


    private URI createContext(String context)
    {
        if (context != null)
        {
            try
            {
                return vf.createURI(context);
            } catch (IllegalArgumentException e)
            {
                System.out.println("Could not create Resource for context: " + context);
            }
        }
        return null;
    }

    private URI createPredicate(String pred)
    {
        URI p = null;
        try
        {
            p = vf.createURI(pred);
        } catch (IllegalArgumentException e)
        {
            System.out.println("Could not create Resource: " + e);
            return null;
        }
        return p;
    }

    private Value createLiteral(String literal)
    {
        ValueFactory f = repo.getValueFactory();
        try
        {
            XMLGregorianCalendar bla = DatatypeFactory.newInstance().newXMLGregorianCalendar(literal);
            return f.createLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar(literal));
        } catch (Exception e)
        {

        }
        try
        {
            return f.createLiteral(Integer.parseInt(literal));
        } catch (NumberFormatException e)
        {

        }
        try
        {
            return f.createLiteral(Double.parseDouble(literal));
        } catch (NumberFormatException e)
        {

        }
        try
        {
            return f.createLiteral(Long.parseLong(literal));
        } catch (NumberFormatException e)
        {

        }
        try
        {
            return f.createLiteral(Float.parseFloat(literal));
        } catch (NumberFormatException e)
        {

        }
        if (literal.equalsIgnoreCase("true")
                || literal.equalsIgnoreCase("false"))
        {
            return f.createLiteral(Boolean.parseBoolean(literal));
        }
        return f.createLiteral(literal);
    }
}
