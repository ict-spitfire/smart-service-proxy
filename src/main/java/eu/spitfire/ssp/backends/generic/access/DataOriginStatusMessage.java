package eu.spitfire.ssp.backends.generic.access;

import eu.spitfire.ssp.backends.generic.WrappedDataOriginStatus;
import eu.spitfire.ssp.backends.generic.observation.InternalUpdateCacheMessage;

/**
 * Created by olli on 11.04.14.
 */
public class DataOriginStatusMessage extends InternalUpdateCacheMessage {

    public DataOriginStatusMessage(WrappedDataOriginStatus dataOriginStatus) {
        super(dataOriginStatus);
    }

}
