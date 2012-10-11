package eu.spitfire_project.smart_service_proxy.TimeProvider;

import org.joda.time.DateTime;
import org.openrdf.OpenRDFException;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
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
public class SimulatedTimeUpdater implements Runnable
{
    private ValueFactory vf = null;
    private Repository repo = null;

    public SimulatedTimeUpdater()
    {
        try
        {
            repo = new HTTPRepository(SimulatedTimeParameters.repoAdr,
                    SimulatedTimeParameters.repositoryID);
            repo.initialize();
            repo.getConnection().setAutoCommit(false);
            vf = repo.getValueFactory();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void run()
    {
        String time;
        if(SimulatedTimeParameters.useDate)
        {
            DateTime simulatedTime = SimulatedTimeParameters.startDate.plusMinutes(SimulatedTimeParameters.elapseMinutes * SimulatedTimeParameters.elapseRound);
            time = SimulatedTimeParameters.dateFormatter.format(simulatedTime.toDate());
        }
        else
        {
            int simulatedTime = (SimulatedTimeParameters.start + SimulatedTimeParameters.elapseRound * SimulatedTimeParameters.elapseMinutes) % SimulatedTimeParameters.overflowMod;
            time = Integer.toString(simulatedTime);
        }

        if(SimulatedTimeParameters.elapseRound == 0)
        {
            insertStatement(time);
        }
        else
        {
            updateStatement(time);
        }

        SimulatedTimeParameters.elapseRound++;
    }

    private void insertStatement(String timeObj)
    {
        Resource s = createSubject(SimulatedTimeParameters.subject);
        URI p = createPredicate(SimulatedTimeParameters.predicate);
        URI res = createContext(SimulatedTimeParameters.context);
        Value o = createLiteral(timeObj);
        try
        {
            RepositoryConnection conn = repo.getConnection();
            try
            {
                conn.add(s, p, o, res);
                //log.info("Added triple: " + s + " - " + p +  " - " + o);
                conn.commit();
            } catch (Exception e)
            {
                e.printStackTrace();
            } finally
            {
                conn.close();
            }
        } catch (OpenRDFException e)
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
        Resource s = createSubject(SimulatedTimeParameters.subject);
        URI p = createPredicate(SimulatedTimeParameters.predicate);
        Resource c = createContext(SimulatedTimeParameters.context);
        try
        {
            repo.getConnection().remove(s, p, null, (Resource) c);
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
