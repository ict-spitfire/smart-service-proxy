package eu.spitfire.ssp.backends.generic.observation;

import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;

/**
 * Created by olli on 10.04.14.
 */
public class InternalUpdateCacheMessage {

    private WrappedDataOriginStatus dataOriginStatus;


    public InternalUpdateCacheMessage(WrappedDataOriginStatus dataOriginStatus){
        this.dataOriginStatus = dataOriginStatus;
    }


    public WrappedDataOriginStatus getDataOriginStatus() {
        return dataOriginStatus;
    }
}
