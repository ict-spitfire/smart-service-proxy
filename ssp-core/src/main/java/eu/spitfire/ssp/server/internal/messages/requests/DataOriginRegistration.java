package eu.spitfire.ssp.server.internal.messages.requests;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.Model;
import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.DataOriginMapper;

import java.util.Date;

/**
 * Internal message to be send downstream
 */
public class DataOriginRegistration<I, D extends DataOrigin<I>>{

    private D dataOrigin;
    private Date expiry;
    private DataOriginMapper httpProxyWebservice;
    private Model initialStatus;
    private SettableFuture<Void> registrationFuture;

    public DataOriginRegistration(D dataOrigin, Model initialStatus, Date expiry, DataOriginMapper httpProxyWebservice,
                                  SettableFuture<Void> registrationFuture){

        this.dataOrigin = dataOrigin;
        this.initialStatus = initialStatus;
        this.expiry = expiry;
        this.httpProxyWebservice = httpProxyWebservice;
        this.registrationFuture = registrationFuture;
    }

    public D getDataOrigin(){
        return this.dataOrigin;
    }


    public Model getInitialStatus() {
        return initialStatus;
    }

    public DataOriginMapper getHttpProxyWebservice() {
        return httpProxyWebservice;
    }

    public String toString(){
        return "DORM: [Data Origin: " + dataOrigin.toString() + ", Backend: " +
                httpProxyWebservice.getBackendName() + "]";
    }

    public SettableFuture<Void> getRegistrationFuture(){
        return this.registrationFuture;
    }

    public Date getExpiry() {
        return expiry;
    }
}
