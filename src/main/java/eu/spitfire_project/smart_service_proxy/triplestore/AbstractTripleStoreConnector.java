package eu.spitfire_project.smart_service_proxy.triplestore;

import org.openrdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTripleStoreConnector
{
	protected static final Logger log = LoggerFactory
			.getLogger(AbstractTripleStoreConnector.class);

	public abstract void connect() throws Exception;

    public abstract boolean isConnected();

    public abstract void close();

	public abstract void disconnect();

	public abstract String[] getMissingValue(String a, String b, int miss, String context);

	public abstract boolean insertTriple(String subject, String pred,
			String object, String context);

    public abstract boolean insertTriple(Statement s);

	public abstract boolean deleteTriple(String subject, String pred,
			String object, String context);

	public abstract boolean updateTriple(String oldS, String oldP, String oldO, String oldC,
			String newS, String newP, String newO, String newC);

	public abstract boolean clear(String... contexts);

    public abstract void commit();

    public abstract void setAutoCommit(boolean autoCommit);

	/**
	 * Statische Methode, liefert die einzige Instanz dieser Klasse zur�ck
	 */
	public static AbstractTripleStoreConnector getInstance()
    {
		return new SesameConnector();
	}

}
