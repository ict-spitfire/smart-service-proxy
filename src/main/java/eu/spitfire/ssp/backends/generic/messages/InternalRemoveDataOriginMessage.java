package eu.spitfire.ssp.backends.generic.messages;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 26.09.13
 * Time: 23:14
 * To change this template use File | Settings | File Templates.
 */
public abstract class InternalRemoveDataOriginMessage<T> {

    private T dataOrigin;

    public InternalRemoveDataOriginMessage(T dataOrigin){
        this.dataOrigin = dataOrigin;
    }

    public T getDataOrigin() {
        return this.dataOrigin;
    }
}
