package net.rcode.core.async;

import java.util.Collection;


/**
 * Simple interface for an asynchronous set that stores strings
 * @author stella
 *
 */
public abstract class AsyncSet {
	/**
	 * Add a value to the set
	 * @param value
	 * @return true if added, false if already existed
	 */
	public abstract Promise<Boolean> add(String value);
	
	public Flow.Initiator<Boolean> addStep(final String value) {
		return new Flow.Initiator<Boolean>() {
			@Override
			public Promise<Boolean> run() throws Throwable {
				return add(value);
			}
			@Override
			public String toString() {
				return "AsyncSet.add";
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
				return "AsyncSet.remove";
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
				return "AsyncSet.contains";
			}
			
		};
	}
	
	/**
	 * @return All members of the set
	 */
	public abstract Promise<Collection<String>> members();

	public Flow.Initiator<Collection<String>> membersStep() {
		return new Flow.Initiator<Collection<String>>() {
			@Override
			public Promise<Collection<String>> run() throws Throwable {
				return members();
			}
			@Override
			public String toString() {
				return "AsyncSet.members";
			}
			
		};
	}
	
	/**
	 * @return Number of items in the set
	 */
	public abstract Promise<Integer> size();
	
	public Flow.Initiator<Integer> sizeStep() {
		return new Flow.Initiator<Integer>() {
			@Override
			public Promise<Integer> run() throws Throwable {
				return size();
			}
			@Override
			public String toString() {
				return "AsyncSet.size";
			}
			
		};
	}
}
