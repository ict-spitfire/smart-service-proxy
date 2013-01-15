package eu.spitfire_project.smart_service_proxy.triplestore;

//import de.rwglab.indexer.helper.Parameters;
import org.openrdf.model.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;

import java.util.List;

/**
 * Class for accessing a sesame triplestore
 *
 * @author Richard Mietz
 */
public class SesameConnector extends AbstractTripleStoreConnector
{
    private Repository repo = null;
    private RepositoryConnection conn = null;
    private ValueFactory vf = null;

    public SesameConnector()
    {
    }

    public void connect() throws RepositoryException
    {
        if (repo == null)
        {
            repo = new HTTPRepository("http://localhost:8080/openrdf-sesame",
                    "sensors");
            repo.initialize();
            conn = repo.getConnection();
            setAutoCommit(false);
            vf = repo.getValueFactory();
        }
    }

    public boolean isConnected()
    {
        if (repo != null && conn != null)
        {
            return true;
        }
        return false;
    }

    public void close()
    {
        if (conn != null)
        {
            try
            {
                conn.close();
            } catch (RepositoryException e)
            {
                log.error("Error while closing triplestore conenction");
            }
        }
    }

    public void disconnect()
    {
        if (repo != null)
        {
            try
            {
                repo.shutDown();
            } catch (RepositoryException e)
            {
                log.error("Error while shutting down triplestore");
            }
        }
    }

    public boolean insertTriple(Statement s)
    {
        try
        {
            conn.add(s);
            return true;
        } catch (RepositoryException e)
        {
            log.error("Error while adding statement.");
            return false;
        }
    }

    public boolean insertTriple(String subject, String pred, String object,
                                String context)
    {
        Resource s = createSubject(subject);
        URI p = createPredicate(pred);
        if (p == null)
        {
            return false;
        }
        URI res = createContext(context);
        Value o = createObject(object);
        if (o == null)
        {
            return false;
        }
        try
        {
            conn.add(s, p, o, res);
        } catch (RepositoryException e)
        {
            log.error("Error while adding statement.");
            return false;
        }

        return true;
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
                log.warn("Could not create Resource for context: " + context);
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
            log.warn("Could not create Resource: " + e);
            return null;
        }
        return p;
    }

    private Value createObject(String object)
    {
        Value o;
        try
        {
            o = vf.createURI(object);
        } catch (IllegalArgumentException e)
        {
            try
            {
                o = createLiteral(object);
            } catch (IllegalArgumentException e2)
            {
                log.warn("Could not create object: " + object);
                return null;
            }
        }
        return o;
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

    private Value createLiteral(String literal)
    {
        ValueFactory f = repo.getValueFactory();
        literal = literal.replaceAll("\"", "");
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

    public void commit()
    {
        if (repo != null)
        {
            try
            {
                conn.commit();
            } catch (RepositoryException e)
            {
                log.warn(e.toString());
            }
        }
    }

    public boolean clear(String... contexts)
    {
        if (repo != null)
        {
            if (contexts.length > 0)
            {
                for (String s : contexts)
                {
                    try
                    {
                        Resource r = repo.getValueFactory().createURI(s);
                        conn.clear(r);
                        StringBuffer buf = new StringBuffer();
                        for (String context : contexts)
                        {
                            buf.append(context);
                            buf.append(", ");
                        }
                        log.info("Contexts deleted: " + buf.toString());
                    } catch (RepositoryException ie)
                    {
                        return false;
                    }
                }
            } else
            {
                try
                {
                    conn.clear();
                    log.info("Complete TripleStore cleared");
                } catch (RepositoryException e)
                {
                    return false;
                }
            }

        }
        //log.info("TripleStore cleared");
        return true;
    }

    @Override
    public void setAutoCommit(boolean autoCommit)
    {
        if (repo != null)
        {
            try
            {
                conn.setAutoCommit(autoCommit);
            } catch (RepositoryException e)
            {
                log.warn(e.toString());
            }
        }
    }

    public boolean deleteTriple(String subject, String pred, String object, String context)
    {
        Resource s = null;
        URI p = null;
        Value o = null;
        Resource c = null;
        try
        {
            if (subject != null && !subject.isEmpty())
            {
                s = createSubject(subject);
            }
            if (pred != null && !pred.isEmpty())
            {
                p = createPredicate(pred);
            }
            if (object != null && !object.isEmpty())
            {
                o = createObject(object);
            }
            if (context != null && !context.isEmpty())
            {
                c = createContext(context);
            }
            conn.remove(s, p, o, (Resource) c);
        } catch (RepositoryException e)
        {
            log.warn("Could not delete triple.");
            return false;
        }
        return false;
    }

    public boolean updateTriple(String oldS, String oldP, String oldO, String oldC,
                                String newS, String newP, String newO, String newC)
    {
        if (deleteTriple(oldS, oldP, oldO, oldC))
        {
            return insertTriple(newS, newP, newO, newC);
        }
        return false;
    }

    public String[] getMissingValue(String a, String b, int miss, String context)
    {
        try
        {
            Resource res;
            URI u;
            Value val;
            URI c = createContext(context);
            List<Statement> types = null;
            switch (miss)
            {
                // pred missing
                case 1:
                    res = createSubject(a);
                    val = createObject(b);
                    types = conn.getStatements(res, null, val, true, c).asList();
                    break;
                // sub missing
                case 2:
                    u = createPredicate(b);
                    val = createObject(b);
                    types = conn.getStatements(null, u, val, true, c).asList();
                    break;
                // obj missing
                case 0:
                default:
                    res = createSubject(a);
                    u = createPredicate(b);
                    types = conn.getStatements(res, u, null, true, c).asList();
                    break;
            }
            String[] result = new String[types.size()];
            for (int i = 0; i < types.size(); i++)
            {
                result[i] = types.get(i).getObject().toString();
            }
            return result;
        } catch (RepositoryException e)
        {
            log.warn("Error while getting connection to triplestore: " + e.getMessage());
        }
        return null;
    }
}
