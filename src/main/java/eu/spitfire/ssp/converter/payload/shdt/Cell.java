package eu.spitfire.ssp.converter.payload.shdt;

/**
 * Created with IntelliJ IDEA.
 * User: henning
 * Date: 12.09.12
 * Time: 14:55
 * To change this template use File | Settings | File Templates.
 */


public class Cell<T> {
    private T value;
    public Cell(T t) { value = t; }
    public void set(T t) { value = t; }
    public T get() { return value; }

}