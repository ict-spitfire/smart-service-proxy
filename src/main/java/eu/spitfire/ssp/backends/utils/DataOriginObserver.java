package eu.spitfire.ssp.backends.utils;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 15.08.13
 * Time: 16:08
 * To change this template use File | Settings | File Templates.
 */
public abstract class DataOriginObserver<T> extends ResourceStatusHandler {

    protected DataOriginObserver(BackendManager<T> backendManager){
        super(backendManager);
    }
}

