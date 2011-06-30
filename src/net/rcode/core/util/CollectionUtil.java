package net.rcode.core.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollectionUtil {
	public static <T> Set<T> set(T...elements) {
		HashSet<T> ret=new HashSet<T>();
		for (T element: elements) ret.add(element);
		return ret;
	}
	
	public static <T> List<T> slice(List<T> l,  int fromIndex) {
		return l.subList(fromIndex, l.size());
	}
}
