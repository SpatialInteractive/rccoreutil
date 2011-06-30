package net.rcode.core.async;

import java.util.Map;

/**
 * Interface for accessing simple maps backed by an async datastore (such as redis)
 * @author stella
 *
 */
public abstract class AsyncMap {
	public abstract Promise<Boolean> put(String name, String value);
	public Flow.Initiator<Boolean> putStep(final String name, final String value) {
		return new Flow.Initiator<Boolean>() {
			@Override
			public Promise<Boolean> run() throws Throwable {
				return put(name, value);
			}
			@Override
			public String toString() {
				return "AsyncMap.put(" + name + ")";
			}
		};
	}
	
	public abstract Promise<String> get(String name);
	public Flow.Initiator<String> getStep(final String name) {
		return new Flow.Initiator<String>() {
			@Override
			public Promise<String> run() throws Throwable {
				return get(name);
			}
			@Override
			public String toString() {
				return "AsyncMap.get(" + name + ")";
			}
		};
	}
	
	/**
	 * Remove an item
	 * @param value
	 * @return true if removed, false if did not exist
	 */
	public abstract Promise<Boolean> remove(String value);
	
	public Flow.Initiator<Boolean> removeStep(final String value) {
		return new Flow.Initiator<Boolean>() {
			@Override
			public Promise<Boolean> run() throws Throwable {
				return remove(value);
			}
			@Override
			public String toString() {
				return "AsyncMap.remove";
			}

		};
	}
	
	/**
	 * Check for existence in the set
	 * @param value
	 * @return true if exists, false if not
	 */
	public abstract Promise<Boolean> contains(String value);
	public Flow.Initiator<Boolean> containsStep(final String value) {
		return new Flow.Initiator<Boolean>() {
			@Override
			public Promise<Boolean> run() throws Throwable {
				return contains(value);
			}
			@Override
			public String toString() {
				return "AsyncMap.contains";
			}
			
		};
	}
	
	public abstract Promise<Map<String, String>> getAll();
	public Flow.Initiator<Map<String,String>> getAllStep() {
		return new Flow.Initiator<Map<String,String>>() {
			@Override
			public Promise<Map<String, String>> run() throws Throwable {
				return getAll();
			}
			@Override
			public String toString() {
				return "AsyncMap.getAll()";
			}
		};
	}
	
	public abstract Promise<Void> putAll(Map<String,String> updates);
	public Flow.Initiator<Void> putAllStep(final Map<String,String> updates) {
		return new Flow.Initiator<Void>() {
			@Override
			public Promise<Void> run() throws Throwable {
				return putAll(updates);
			}
			
			@Override
			public String toString() {
				return "AsyncMap.putAll(...)";
			}
		};
	}
}
