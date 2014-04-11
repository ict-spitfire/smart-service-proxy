package eu.spitfire.ssp.backends.generic.messages;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 26.09.13
 * Time: 23:14
 * To change this template use File | Settings | File Templates.
 */
public class InternalRemoveDataOriginMessage<T> {

    private T identifier;

    public InternalRemoveDataOriginMessage(T identifier){
        this.identifier = identifier;
    }

    public T getIdentifier() {
        return identifier;
    }

}
