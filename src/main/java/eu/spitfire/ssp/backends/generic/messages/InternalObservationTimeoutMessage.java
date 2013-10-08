package eu.spitfire.ssp.backends.generic.messages;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 30.09.13
 * Time: 19:03
 * To change this template use File | Settings | File Templates.
 */
public abstract class InternalObservationTimeoutMessage<T> {

    private T dataOrigin;

    public InternalObservationTimeoutMessage(T dataOrigin){
        this.dataOrigin = dataOrigin;
    }

    public T getDataOrigin(){
        return this.dataOrigin;
    }
}
