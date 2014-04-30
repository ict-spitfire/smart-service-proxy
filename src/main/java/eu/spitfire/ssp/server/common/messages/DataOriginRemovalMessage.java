package eu.spitfire.ssp.server.common.messages;

import eu.spitfire.ssp.backends.generic.DataOrigin;

/**
 * Created by olli on 16.04.14.
 */
public class DataOriginRemovalMessage<T> {

    private DataOrigin<T> dataOrigin;

    public DataOriginRemovalMessage(DataOrigin<T> dataOrigin) {
        this.dataOrigin = dataOrigin;
    }

    public DataOrigin<T> getDataOrigin() {
        return dataOrigin;
    }
}
