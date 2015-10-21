package eu.spitfire.ssp.server.internal.message;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backend.generic.DataOrigin;
import eu.spitfire.ssp.backend.generic.DataOriginMapper;
import com.hp.hpl.jena.rdf.model.Model;

import java.util.Date;

/**
 * Internal message to be send downstream
 */
public class DataOriginRegistrationRequest<I, D extends DataOrigin<I>>{

    private D dataOrigin;
    private Date expiry;
    private DataOriginMapper httpProxyWebservice;
    private Model initialStatus;
    private SettableFuture<Void> registrationFuture;

    public DataOriginRegistrationRequest(D dataOrigin, Model initialStatus, Date expiry, DataOriginMapper httpProxyWebservice,
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

    public DataOriginMapper getHttpProxyService() {
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
