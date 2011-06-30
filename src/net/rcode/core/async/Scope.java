package net.rcode.core.async;

/**
 * Asynchronous operations happen in the context of a LogicScope.
 * This allows us to centralize exception handling among other
 * things.
 * <p>
 * Scopes are bound to a thread for the lifetime of a step in an
 * async process.  They can also be captured, typically prior to
 * going async and then re-instated later.
 * 
 * @author stella
 *
 */
public class Scope {
	// -- interfaces/classes
	public static interface Callback<T> {
		public T invoke() throws Exception;
	}
	
	public static interface Errback {
		public void handleError(Throwable t) throws Throwable;
	}
	
	public abstract static class Step implements Callback<Void> {
		public abstract void step() throws Exception;
		
		@Override
		public Void invoke() throws Exception {
			step();
			return null;
		}
	}
	
	// -- static members
	private static final ThreadLocal<Scope> current=new ThreadLocal<Scope>();
	
	/**
	 * Capture the current execution scope
	 * @return an existing scope or null if none
	 */
	public static Scope capture() {
		return current.get();
	}
	
	/**
	 * Create a new Scope and invoke the given callback within it
	 * @param cb
	 * @throws Exception 
	 */
	public static void invoke(Callback<?> cb) {
		Scope scope=new Scope();
		scope.invokeInternal(cb);
	}
	
	/**
	 * Create a new Scope and run the given evaluation within it
	 * @param <T>
	 * @param cb
	 * @return
	 * @throws Exception
	 */
	public static <T> T evaluate(Callback<T> cb) {
		Scope scope=new Scope();
		return scope.invokeInternal(cb);
	}

	/**
	 * Create a new Scope and invoke the given callback within it
	 * @param cb
	 * @throws Exception 
	 */
	public static void invoke(Callback<?> cb, Errback firstResponder) {
		Scope scope=new Scope();
		scope.firstResponder=firstResponder;
		scope.invokeInternal(cb);
	}
	
	/**
	 * Create a new Scope and run the given evaluation within it
	 * @param <T>
	 * @param cb
	 * @return
	 * @throws Exception
	 */
	public static <T> T evaluate(Callback<T> cb, Errback firstResponder) {
		Scope scope=new Scope();
		scope.firstResponder=firstResponder;
		return scope.invokeInternal(cb);
	}
	
	/**
	 * Invoke the given callback in a given scope
	 * @param scope
	 * @param cb
	 * @throws Exception
	 */
	public static void invoke(Scope scope, Callback<?> cb) {
		if (scope==null) {
			try {
				cb.invoke();
			} catch (Throwable t) {
				throwException(t);
			}
		}
		else scope.invokeInternal(cb);
	}
	
	/**
	 * Evaluate a callback in the context of an existing scope
	 * @param <T>
	 * @param scope
	 * @param cb
	 * @return
	 * @throws Exception
	 */
	public static <T> T evaluate(Scope scope, Callback<T> cb) {
		if (scope==null) {
			try {
				return cb.invoke();
			} catch (Throwable t) {
				throwException(t);
				return null;
			}
		}
		else return scope.invokeInternal(cb);
	}
	
	private static void throwException(Throwable t) {
		if (t instanceof RuntimeException) throw (RuntimeException)t;
		else if (t instanceof Error) throw (Error)t;
		else throw new RuntimeException(t);
	}
	
	// -- instance members
	Errback firstResponder;
	
	private Scope() {
	}
	
	protected <T> T invokeInternal(Callback<T> cb) {
		Scope orig=current.get();
		current.set(this);
		try {
			return cb.invoke();
		} catch (Throwable t) {
			if (firstResponder!=null) {
				try {
					firstResponder.handleError(t);
				} catch (Throwable th) {
					t=th;
				}
			}
			
			throwException(t);
			return null;
		} finally {
			current.set(orig);
		}
	}

}
