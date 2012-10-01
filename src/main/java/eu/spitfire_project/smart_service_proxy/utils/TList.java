package eu.spitfire_project.smart_service_proxy.utils;

import java.util.ArrayList;

public class TList {
	protected ArrayList<Object> list;
	private int limitSize;
	
	public TList() {
		list = new ArrayList<Object>();
		limitSize = Integer.MAX_VALUE;
	}
	
	public TList(int limitSize) {
		list = new ArrayList<Object>();
		this.limitSize = limitSize;
	}

	public int len() { 
		int min = limitSize;
		if (min > list.size())
			min = list.size();
		return min; 
	}
	
	public void clear() {
		list.clear();
	}
	
	public void setLimitSize(int limitSize) { 
		this.limitSize = limitSize; 
		if (list.size() > limitSize) {
			for (int i=limitSize; i<list.size(); i++)
				list.remove(i);
		}
	}
	
	public boolean isEmpty() { return (list.size()==0); }
	public boolean outRange(int index) { return (index < limitSize && index < list.size()); }

	public void enList(Object o) { list.add(o); }
	
	public void enList(int index, Object o) { list.add(index, o); }
	
	public void enQueue(Object o) { 
		if (list.size() >= limitSize)
			list.remove(0);
		list.add(o);
	}
	
	public void enStack(Object o) { 
		if (list.size() < limitSize)
			list.add(o);
	}	
	
	public Object deQueue() { return deList(0); }
	
	public Object deList(int index) {
		Object rs = null;
		rs = list.get(index);
		list.remove(index);
		return rs;
	}
	
	public Object deStack() { 
		return deList(list.size()-1); 
	}
	
	public Object get(int index) { 
		return list.get(index); 
	}
	
	public Object getLast() {
		return list.get(list.size()-1); 
	}
	
	public void set(int index, Object o) { list.set(index, o); }
	
	public void remove(Object o) { list.remove(o); }
	public void remove(int index) { list.remove(index); }
}
