package eu.spitfire.ssp.backends.generic.messages;

import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.HttpSemanticProxyWebservice;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
public class InternalRegisterDataOriginMessage<T>{

    private DataOrigin<T> dataOrigin;
    private HttpSemanticProxyWebservice httpProxyWebservice;

    public InternalRegisterDataOriginMessage(DataOrigin<T> dataOrigin, HttpSemanticProxyWebservice httpProxyWebservice){
        this.httpProxyWebservice = httpProxyWebservice;
        this.dataOrigin = dataOrigin;
    }

    public DataOrigin<T> getDataOrigin(){
        return this.dataOrigin;
    }


    public HttpSemanticProxyWebservice getHttpProxyWebservice() {
        return httpProxyWebservice;
    }
}
