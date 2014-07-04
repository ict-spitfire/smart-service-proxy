package eu.spitfire.ssp.server.common.messages;

import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.ProtocolConversion;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
public class DataOriginRegistrationMessage<T>{

    private DataOrigin<T> dataOrigin;
    private ProtocolConversion httpProxyWebservice;

    public DataOriginRegistrationMessage(DataOrigin<T> dataOrigin, ProtocolConversion httpProxyWebservice){

        this.httpProxyWebservice = httpProxyWebservice;
        this.dataOrigin = dataOrigin;
    }

    public DataOrigin<T> getDataOrigin(){
        return this.dataOrigin;
    }


    public ProtocolConversion getHttpProxyWebservice() {
        return httpProxyWebservice;
    }

    public String toString(){
        return "DORM: [Data Origin: " + dataOrigin.toString() + ", Backend: " +
                httpProxyWebservice.getBackendName() + "]";
    }
}
