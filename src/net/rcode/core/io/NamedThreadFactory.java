/**
 * 
 */
package net.rcode.core.io;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {
	private String baseName;
	
	private TreeSet<Integer> indexes=new TreeSet<Integer>();
	private int nextIndexSeq;
	
	public NamedThreadFactory(String baseName) {
		this.baseName=baseName;
	}
	
	synchronized int nextIndex() {
		if (indexes.isEmpty()) {
			return ++nextIndexSeq;
		} else {
			Iterator<Integer> iter=indexes.iterator();
			int ret=iter.next();
			iter.remove();
			return ret;
		}
	}
	
	synchronized void recycleIndex(int index) {
		indexes.add(index);
	}
	
	@Override
	public Thread newThread(final Runnable r) {
		final int index=nextIndex();
		Thread ret=new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					r.run();
				} finally {
					recycleIndex(index);
				}
			}
		});
		ret.setName(baseName + "-" + index);
		ret.setDaemon(false);
		return ret;
	}
	
}