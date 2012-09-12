package eu.spitfire_project.smart_service_proxy.utils;

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