package net.rcode.core.async;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traces asynchronous execution
 * @author stella
 *
 */
public class AsyncTrace {
	public static final boolean ENABLED=false;
	public static AsyncTrace INSTANCE=new AsyncTrace();
	private static boolean monitoring;
	
	private ConcurrentHashMap<Promise,Long> BROKEN_PROMISES=new ConcurrentHashMap<Promise, Long>();
	
	public static void monitor() {
		if (!ENABLED || monitoring) return;
		monitoring=true;
		
		Timer promiseMonitor=new Timer("AsyncTrace", true);
		final TimerTask task=new TimerTask() {
			@Override
			public void run() {
				System.err.println("--- Monitor Tick ---");
				if (!INSTANCE.BROKEN_PROMISES.isEmpty()) {
					System.err.println(INSTANCE.toString());
				}
			}
		};
		
		promiseMonitor.schedule(task, 10000, 10000);
	}
	
	public static void addBrokenPromise(Promise p) {
		INSTANCE.BROKEN_PROMISES.putIfAbsent(p, System.currentTimeMillis());
	}
	
	public static void removeBrokenPromise(Promise p) {
		INSTANCE.BROKEN_PROMISES.remove(p);
	}
	
	public String toString() {
		StringBuilder sb=new StringBuilder();
		Map<Promise,Long> snapshot=new HashMap<Promise, Long>(BROKEN_PROMISES);
		long time=System.currentTimeMillis();
		sb.append("Current promises:\n");
		for (Map.Entry<Promise, Long> entry: snapshot.entrySet()) {
			sb.append('\t');
			sb.append(entry.getKey());
			sb.append(" (age=");
			sb.append(time-entry.getValue());
			sb.append("ms)");
		}
		
		return sb.toString();
	}
}
