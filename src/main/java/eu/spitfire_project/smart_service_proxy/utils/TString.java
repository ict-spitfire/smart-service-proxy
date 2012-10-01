package eu.spitfire_project.smart_service_proxy.utils;

import java.util.ArrayList;

public class TString {
	ArrayList<String> strList;
	char seperator;

	public TString()	{
		seperator = 32;
		strList = new ArrayList<String>();
	}
	
	public TString(char sep)	{
		seperator = sep;
		strList = new ArrayList<String>();
	}
	
	public TString(String st) {
		seperator = 32;
		strList = new ArrayList<String>();
		setStr(st);
	}
	
	public TString(String st, char sep) {
		seperator = sep;
		strList = new ArrayList<String>();
		setStr(st);
	}

	public void setSeperator(char c) {
		seperator = c;
	}

	public int len() {
		return strList.size();
	}

	 public void clear() {
	     strList.clear();
	 }

	public void setStr(String st) {
		strList = new ArrayList<String>();
		String str = st;
		if (st.charAt(st.length()-1) != seperator)
		 str = str + seperator;
		int index = 0;
		int fromIndex = 0;
		while (index < str.length()-1) {
			index = str.indexOf(seperator, fromIndex);
			String sub = str.substring(fromIndex, index);
			//if (sub != null) 
				strList.add(sub);
			fromIndex = index + 1;
		}
	}

	public String getStrAt(int index) {
		String rs = null;
		if (index < strList.size())
			rs = strList.get(index);; 
			
		return rs;
	}
	
	public String getStrAtEnd() {
		return strList.get(strList.size()-1);
	}

	 public void setStrAt(int index, String s) {
	     strList.add(index, s);
		strList.remove(index+1);
	}

	public String getStr() {
		String str = "";
		for (int i=0; i<strList.size()-1; i++) {
			str += strList.get(i);
			str += seperator;
		}
		str += strList.get(strList.size()-1);
	
		return str;
	}

	public void addInt(int value) {
		strList.add(String.valueOf(value));
	}

	public void addDouble(double value) {
		strList.add(String.valueOf(value));
	}

	public void addStr(String value) {
		strList.add(value);
	}

	public boolean isNumber() {
		String s = this.getStr();
		for (int i=0; i<s.length(); i++) {
			if ((int)s.charAt(i)<48 || (int)s.charAt(i)>57)
				return false;
		}
		return true;
	}
	
	public static String removeBlock(String str, String begin, String end) {
		String rs = str;
		boolean finished = false;
		while (!finished) {
			int p1 = rs.indexOf('<', 0);
			if (p1 >= 0) {
				int p2 = rs.indexOf(end, p1);
				if (p2 > 0) {
					if (p1 > 0) {
						String tmp = rs.substring(0, p1-1);
						tmp = tmp + rs.substring(p2+1);
						rs = tmp;
					} else
						rs = rs.substring(p2+1);	
				} else finished = true;
			} else finished = true;
		}
		
		return rs;		
	}
	
	public static String removeBlock(String str, String end) {
		String rs = str;
		boolean finished = false;
		while (finished) {
			int p2 = rs.indexOf(end);
			if (p2 > 0)
				rs = rs.substring(p2+1);
			else
				finished = true;
		}
		
		return rs;		
	}
	
	/*//Debug & test
	public static void main(String[] args) {
		TString s = new TString();
		s.setStr("2004 03 30 08 11 57    2.1997");
		System.out.println("Length: "+String.valueOf(s.getSize()));
		System.out.println("Elements: ");
		for (int i=0; i<s.getSize(); i++) {
			System.out.println("|"+s.getStrAt(i)+"|");
		}
	}
	//End Debug & Test*/
}
